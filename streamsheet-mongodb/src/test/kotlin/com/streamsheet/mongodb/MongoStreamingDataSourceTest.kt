package com.streamsheet.mongodb

import com.streamsheet.core.exception.ValidationException
import com.streamsheet.mongodb.filter.MongoFilter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.mongodb.core.ExecutableFindOperation
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.util.stream.Stream
import java.lang.reflect.Field

@ExtendWith(MockitoExtension::class)
@DisplayName("MongoDB 스트리밍 데이터 소스 테스트")
class MongoStreamingDataSourceTest {

    @Mock
    private lateinit var mongoTemplate: MongoTemplate

    // 테스트용 더미 엔티티
    data class TestDocument(val name: String, val age: Int)

    // NOTE: 제네릭 타입 소거(Type Erasure)로 인한 형변환 경고를 억제합니다.
    // Suppress unchecked cast warnings due to type erasure.
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> setupMockQuery(data: List<T>): Stream<T> {
        val stream = mock(Stream::class.java) as Stream<T>
        val executableFind = mock(ExecutableFindOperation.ExecutableFind::class.java) as ExecutableFindOperation.ExecutableFind<T>
        val terminatingFind = mock(ExecutableFindOperation.TerminatingFind::class.java) as ExecutableFindOperation.TerminatingFind<T>

        lenient().`when`(mongoTemplate.query(any<Class<T>>())).thenReturn(executableFind)
        lenient().`when`(executableFind.`as`(any<Class<T>>())).thenReturn(executableFind)
        lenient().`when`(executableFind.matching(any(Query::class.java))).thenReturn(terminatingFind)
        lenient().`when`(terminatingFind.stream()).thenReturn(stream)
        
        var closeHandler: Runnable? = null
        lenient().`when`(stream.onClose(any())).thenAnswer { 
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
    @DisplayName("전체 데이터를 스트리밍으로 조회한다")
    fun `stream() should return sequence of all data`() {
        // Given
        val data = listOf(
            TestDocument("User1", 20),
            TestDocument("User2", 30)
        )
        setupMockQuery(data)

        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class)

        // When
        val resultSequence = dataSource.stream()
        val resultList = resultSequence.toList()

        // Then
        assertEquals(2, resultList.size)
        assertEquals("User1", resultList[0].name)
    }

    @Test
    @DisplayName("인젝션 가능성이 있는 필터 키는 차단되어야 한다")
    fun `should throw exception for invalid filter keys`() {
        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class)
        val maliciousFilter = mapOf("\$where" to "true")

        val exception = assertThrows<ValidationException> {
            dataSource.stream(maliciousFilter)
        }
        assertEquals("filter.key", exception.fieldName)
        assertEquals("\$where", exception.invalidValue)
    }

    @Test
    @DisplayName("정상적인 필터 키는 허용되어야 한다")
    fun `should allow valid filter keys`() {
        // Given
        setupMockQuery(emptyList<TestDocument>())
        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class)
        val validFilter = mapOf("user.name" to "Antigravity", "age" to 25)
        
        // When & Then (No exception)
        dataSource.stream(validFilter).toList()
    }

    @Test
    @DisplayName("Map 형태의 필터 값은 차단되어야 한다 (operator injection 방지)")
    fun `should reject map filter values`() {
        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class)
        val filter = mapOf("age" to mapOf("\$gt" to 1))

        val exception = assertThrows<ValidationException> {
            dataSource.stream(filter)
        }
        assertEquals("filter.value", exception.fieldName)
        assertNotNull(exception.invalidValue)
    }

    @Test
    @DisplayName("IN/Range/Regex typed filter를 적용할 수 있어야 한다")
    fun `should apply typed filters`() {
        setupMockQuery(emptyList<TestDocument>())
        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class)

        dataSource.stream(
            mapOf(
                "age" to MongoFilter.In(listOf(10, 20, 30)),
                "name" to MongoFilter.Regex("User.*"),
                "score" to MongoFilter.Range(gte = 0, lt = 100),
            )
        ).toList()
    }

    @Test
    @DisplayName("create 팩토리 메서드로 인스턴스를 생성할 수 있다")
    fun `should create instance using factory method`() {
        setupMockQuery(emptyList<TestDocument>())
        val dataSource = MongoStreamingDataSource.create<TestDocument>(mongoTemplate)
        assertNotNull(dataSource)
        assertEquals("MongoDB:TestDocument->TestDocument", dataSource.sourceName)
        dataSource.stream().toList() // 실제 호출 추가
    }

    @Test
    @DisplayName("Java Class 기반 create 팩토리 메서드로 인스턴스를 생성할 수 있다")
    fun `should create instance using class factory method`() {
        setupMockQuery(emptyList<TestDocument>())
        val dataSource = MongoStreamingDataSource.create(mongoTemplate, TestDocument::class.java)
        assertNotNull(dataSource)
        assertEquals("MongoDB:TestDocument->TestDocument", dataSource.sourceName)
        dataSource.stream().toList()
    }

    @Test
    @DisplayName("createWithProjection 팩토리 메서드로 인스턴스를 생성할 수 있다")
    fun `should create instance with projection using factory method`() {
        data class TestProjection(val name: String)
        setupMockQuery(emptyList<TestProjection>())
        val dataSource = MongoStreamingDataSource.createWithProjection<TestDocument, TestProjection>(mongoTemplate)
        assertNotNull(dataSource)
        assertEquals("MongoDB:TestDocument->TestProjection", dataSource.sourceName)
        dataSource.stream().toList() // 실제 호출 추가
    }

    @Test
    @DisplayName("Java Class 기반 createWithProjection 팩토리 메서드로 인스턴스를 생성할 수 있다")
    fun `should create instance with projection using class factory method`() {
        data class TestProjection(val name: String)
        setupMockQuery(emptyList<TestProjection>())
        val dataSource = MongoStreamingDataSource.createWithProjection(
            mongoTemplate,
            TestDocument::class.java,
            TestProjection::class.java,
        )
        assertNotNull(dataSource)
        assertEquals("MongoDB:TestDocument->TestProjection", dataSource.sourceName)
        dataSource.stream().toList()
    }

    @Test
    @DisplayName("기존 쿼리가 있는 상태에서 필터를 추가해도 정상 동작해야 한다")
    fun `stream(filter) should combine with base query`() {
        // Given
        val baseQuery = Query.query(org.springframework.data.mongodb.core.query.Criteria.where("status").`is`("ACTIVE"))
        setupMockQuery(emptyList<TestDocument>())
        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class, baseQuery)
        val filter = mapOf("name" to "Antigravity")
        
        // When
        dataSource.stream(filter).toList()

        // Then
        val queryCaptor = ArgumentCaptor.forClass(Query::class.java)
        val executableFind = mock(ExecutableFindOperation.ExecutableFind::class.java) as ExecutableFindOperation.ExecutableFind<TestDocument>
        verify(mongoTemplate).query(TestDocument::class.java)
    }

    @Test
    @DisplayName("생성 시 주입된 기본 쿼리가 stream() 호출 시에도 적용되어야 한다")
    fun `stream() should use base query from constructor`() {
        // Given
        val baseQuery = Query.query(org.springframework.data.mongodb.core.query.Criteria.where("status").`is`("ACTIVE"))
        setupMockQuery(emptyList<TestDocument>())
        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class, baseQuery)
        
        // When
        dataSource.stream().toList()

        // Then
        verify(mongoTemplate).query(TestDocument::class.java)
    }

    // NOTE: 제네릭 타입 소거(Type Erasure)로 인한 형변환 경고를 억제합니다.
    // Suppress unchecked cast warnings due to type erasure.
    @Suppress("UNCHECKED_CAST")
    @Test
    @DisplayName("close 호출 시 모든 스트림을 종료하고 리스트에서 제거한다")
    fun `close() should close all streams and clear list`() {
        // Given
        val mockStream1 = mock(Stream::class.java) as Stream<TestDocument>
        val executableFind = mock(ExecutableFindOperation.ExecutableFind::class.java) as ExecutableFindOperation.ExecutableFind<TestDocument>
        val terminatingFind = mock(ExecutableFindOperation.TerminatingFind::class.java) as ExecutableFindOperation.TerminatingFind<TestDocument>

        lenient().`when`(mongoTemplate.query(any<Class<TestDocument>>())).thenReturn(executableFind)
        lenient().`when`(executableFind.`as`(any<Class<TestDocument>>())).thenReturn(executableFind)
        lenient().`when`(executableFind.matching(any(Query::class.java))).thenReturn(terminatingFind)
        lenient().`when`(terminatingFind.stream()).thenReturn(mockStream1)
        
        // Mock onClose
        var closeHandler: Runnable? = null
        lenient().`when`(mockStream1.onClose(any())).thenAnswer { 
            closeHandler = it.getArgument(0)
            mockStream1 
        }
        lenient().`when`(mockStream1.close()).thenAnswer { 
            closeHandler?.run()
            null
        }
        
        // Mock iterator for sequence conversion
        lenient().`when`(mockStream1.iterator()).thenReturn(emptyList<TestDocument>().iterator())

        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class, TestDocument::class)

        // When
        // 시퀀스를 생성만 하고 소비하지 않음 (Iterator를 가져오기만 함)
        val iterator = dataSource.stream().iterator()
        
        val field: Field = dataSource.javaClass.getDeclaredField("activeStreams")
        field.isAccessible = true
        val activeStreams = field.get(dataSource) as List<*>
        assertEquals(1, activeStreams.size)

        dataSource.close()

        // Then
        verify(mockStream1, atLeastOnce()).close()
        assertEquals(0, activeStreams.size)
    }
}
