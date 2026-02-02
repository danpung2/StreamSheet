package com.streamsheet.mongodb

import com.streamsheet.core.exception.ValidationException
import com.streamsheet.mongodb.filter.MongoFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import java.lang.reflect.Field
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
@DisplayName("MongoDB Aggregation 스트리밍 데이터 소스 테스트")
class MongoAggregationStreamingDataSourceTest {

    @Mock
    private lateinit var mongoTemplate: MongoTemplate

    data class TestDocument(val name: String, val age: Int)

    // NOTE: 제네릭 타입 소거(Type Erasure)로 인한 형변환 경고를 억제합니다.
    // Suppress unchecked cast warnings due to type erasure.
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> setupMockAggregateStream(data: List<T>): Stream<T> {
        val stream = mock(Stream::class.java) as Stream<T>
        val streamAny = stream as Stream<Any>

        lenient().`when`(mongoTemplate.getCollectionName(ArgumentMatchers.any(Class::class.java))).thenReturn("test_collection")
        lenient().`when`(
            mongoTemplate.aggregateStream(
                ArgumentMatchers.any(Aggregation::class.java),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any<Class<Any>>()
            )
        ).thenReturn(streamAny)

        var closeHandler: Runnable? = null
        lenient().`when`(stream.onClose(ArgumentMatchers.any())).thenAnswer {
            closeHandler = it.getArgument(0)
            stream
        }
        lenient().`when`(stream.close()).thenAnswer {
            closeHandler?.run()
            null
        }
        lenient().`when`(stream.iterator()).thenReturn(data.iterator())

        return stream
    }

    @Test
    @DisplayName("Aggregation 결과를 Sequence로 스트리밍할 수 있어야 한다")
    fun `stream should return sequence of data`() {
        val data = listOf(TestDocument("User1", 20), TestDocument("User2", 30))
        setupMockAggregateStream(data)

        val pipeline = emptyList<AggregationOperation>()
        val dataSource = MongoAggregationStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class, pipeline)

        val result = dataSource.stream().toList()
        assertEquals(2, result.size)
        assertEquals("User1", result[0].name)
    }

    @Test
    @DisplayName("인젝션 가능성이 있는 필터 키는 차단되어야 한다")
    fun `should reject invalid filter keys`() {
        val pipeline = emptyList<AggregationOperation>()
        val dataSource = MongoAggregationStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class, pipeline)

        val exception = assertThrows<ValidationException> {
            dataSource.stream(mapOf("\$where" to "true")).toList()
        }
        assertEquals("filter.key", exception.fieldName)
    }

    @Test
    @DisplayName("Map 형태의 필터 값은 차단되어야 한다 (operator injection 방지)")
    fun `should reject map filter values`() {
        val pipeline = emptyList<AggregationOperation>()
        val dataSource = MongoAggregationStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class, pipeline)

        val exception = assertThrows<ValidationException> {
            dataSource.stream(mapOf("age" to mapOf("\$gt" to 1))).toList()
        }
        assertEquals("filter.value", exception.fieldName)
        assertNotNull(exception.invalidValue)
    }

    @Test
    @DisplayName("typed filter(IN/Range/Regex)를 match로 적용할 수 있어야 한다")
    fun `should accept typed filters`() {
        setupMockAggregateStream(emptyList<TestDocument>())
        val pipeline = emptyList<AggregationOperation>()
        val dataSource = MongoAggregationStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class, pipeline)

        dataSource.stream(
            mapOf(
                "age" to MongoFilter.In(listOf(10, 20)),
                "name" to MongoFilter.Regex("User.*"),
            )
        ).toList()
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    @DisplayName("Sequence가 소진되면 내부 Stream이 close되어 activeStreams에서 제거되어야 한다")
    fun `should close stream on exhaustion`() {
        setupMockAggregateStream(listOf(TestDocument("User1", 20)))
        val pipeline = emptyList<AggregationOperation>()
        val dataSource = MongoAggregationStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class, pipeline)

        val seq = dataSource.stream()
        assertEquals(1, seq.toList().size)

        val field: Field = dataSource.javaClass.getDeclaredField("activeStreams")
        field.isAccessible = true
        val activeStreams = field.get(dataSource) as List<*>
        assertTrue(activeStreams.isEmpty())
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    @DisplayName("close() 호출 시 activeStreams에 남아있는 Stream들이 종료되어야 한다")
    fun `close should close active streams`() {
        val stream = setupMockAggregateStream(emptyList<TestDocument>())
        val pipeline = emptyList<AggregationOperation>()
        val dataSource = MongoAggregationStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class, pipeline)

        // NOTE: Sequence를 생성만 하고 소비하지 않습니다.
        // Create sequence but do not consume.
        dataSource.stream().iterator()

        val field: Field = dataSource.javaClass.getDeclaredField("activeStreams")
        field.isAccessible = true
        val activeStreams = field.get(dataSource) as List<*>
        assertEquals(1, activeStreams.size)

        dataSource.close()
        verify(stream).close()
        assertTrue((field.get(dataSource) as List<*>).isEmpty())
    }
    @Test
    @DisplayName("부분 소비 후 close 호출 시 리소스가 정리되어야 한다")
    fun `should cleanup resources when closed after partial consumption`() {
        val data = listOf(TestDocument("User1", 20), TestDocument("User2", 30))
        val stream = setupMockAggregateStream(data)
        val pipeline = emptyList<AggregationOperation>()
        val dataSource = MongoAggregationStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class, pipeline)

        // When
        dataSource.stream().take(1).toList() // Partial

        val field: Field = dataSource.javaClass.getDeclaredField("activeStreams")
        field.isAccessible = true
        // 부분 소비 시에는 스트림이 아직 닫히지 않아 1개 남아있어야 함
        val activeStreamsBefore = field.get(dataSource) as List<*>
        assertEquals(1, activeStreamsBefore.size, "Stream should remain active if not fully consumed")

        dataSource.close()

        // Then
        verify(stream).close()
        assertTrue((field.get(dataSource) as List<*>).isEmpty(), "activeStreams should be empty after close()")
    }
}
