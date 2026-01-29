package com.streamsheet.mongodb

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.util.stream.Stream
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("MongoDB 스트리밍 데이터 소스 테스트")
class MongoStreamingDataSourceTest {

    @Mock
    private lateinit var mongoTemplate: MongoTemplate

    // 테스트용 더미 엔티티
    data class TestDocument(val name: String, val age: Int)

    @Test
    @DisplayName("전체 데이터를 스트리밍으로 조회한다")
    fun `stream() should return sequence of all data`() {
        // Given
        val data = listOf(
            TestDocument("User1", 20),
            TestDocument("User2", 30)
        )
        // Mock Stream
        val stream = data.stream()
        
        // Mock MongoTemplate behavior
        // NOTE: Kotlin에서 any() 사용 시 NPE 주의 (구체적인 클래스 타입 명시)
        // NOTE: Use specific class type with any() to avoid NPE in Kotlin
        `when`(mongoTemplate.stream(any(Query::class.java), eq(TestDocument::class.java)))
            .thenReturn(stream)

        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class)

        // When
        val resultSequence = dataSource.stream()
        val resultList = resultSequence.toList()

        // Then
        assertEquals(2, resultList.size)
        assertEquals("User1", resultList[0].name)
        assertEquals("User2", resultList[1].name)
        
        // Verify empty query was used
        val queryCaptor = ArgumentCaptor.forClass(Query::class.java)
        verify(mongoTemplate).stream(queryCaptor.capture(), eq(TestDocument::class.java))
        val capturedQuery = queryCaptor.value
        assertTrue(capturedQuery.queryObject.isEmpty())
    }

    @Test
    @DisplayName("필터 조건에 맞춰 쿼리를 생성하고 데이터를 조회한다")
    fun `stream(filter) should build correct query and return filtered data`() {
        // Given
        val data = listOf(TestDocument("TargetUser", 25))
        val stream = data.stream()
        
        `when`(mongoTemplate.stream(any(Query::class.java), eq(TestDocument::class.java)))
            .thenReturn(stream)

        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class)
        val filter = mapOf("name" to "TargetUser", "age" to 25)

        // When
        val resultSequence = dataSource.stream(filter)
        resultSequence.toList() // Consume to trigger stream

        // Then
        val queryCaptor = ArgumentCaptor.forClass(Query::class.java)
        verify(mongoTemplate).stream(queryCaptor.capture(), eq(TestDocument::class.java))
        
        val capturedQuery = queryCaptor.value
        val criteriaObj = capturedQuery.queryObject
        
        assertEquals("TargetUser", criteriaObj["name"])
        assertEquals(25, criteriaObj["age"])
    }

    @Test
    @DisplayName("close 호출 시 관리 중인 모든 스트림을 종료한다")
    fun `close() should close all opened streams`() {
        // Given
        val mockStream1 = mock(Stream::class.java) as Stream<TestDocument>
        val mockStream2 = mock(Stream::class.java) as Stream<TestDocument>
        
        // NOTE: toKotlinSequence() 호출 시 iterator()가 즉시 실행되므로 NPE 방지를 위해 Mocking 필요
        // NOTE: Mock iterator() to prevent NPE as toKotlinSequence() invokes it immediately
        `when`(mockStream1.iterator()).thenReturn(emptyList<TestDocument>().iterator())
        `when`(mockStream2.iterator()).thenReturn(emptyList<TestDocument>().iterator())
        
        `when`(mongoTemplate.stream(any(Query::class.java), eq(TestDocument::class.java)))
            .thenReturn(mockStream1)
            .thenReturn(mockStream2)

        val dataSource = MongoStreamingDataSource(mongoTemplate, TestDocument::class)

        // When
        // Open two streams
        dataSource.stream().toList()
        dataSource.stream().toList()
        
        // Close datasource
        dataSource.close()

        // Then
        verify(mockStream1, times(1)).close()
        verify(mockStream2, times(1)).close()
    }
}
