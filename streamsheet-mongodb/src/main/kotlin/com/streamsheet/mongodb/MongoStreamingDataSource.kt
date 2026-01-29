package com.streamsheet.mongodb

import com.streamsheet.core.datasource.StreamingDataSource
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 * MongoDB 기반 스트리밍 데이터 소스
 * MongoDB-based streaming data source
 *
 * NOTE: MongoTemplate의 stream() 메서드를 사용하여 커서 기반 스트리밍을 제공합니다.
 * Uses MongoTemplate's stream() method to provide cursor-based streaming.
 *
 * @param T 엔티티 타입 / Entity type
 * @param mongoTemplate MongoTemplate 인스턴스 / MongoTemplate instance
 * @param entityClass 엔티티 클래스 / Entity class
 */
class MongoStreamingDataSource<T : Any>(
    private val mongoTemplate: MongoTemplate,
    private val entityClass: KClass<T>
) : StreamingDataSource<T> {

    // NOTE: 생성된 Java Stream들을 추적하여 close() 시점에 일괄 해제
    private val activeStreams = mutableListOf<Stream<T>>()

    override val sourceName: String
        get() = "MongoDB:${entityClass.simpleName}"

    override fun stream(): Sequence<T> {
        return stream(emptyMap())
    }

    override fun stream(filter: Map<String, Any>): Sequence<T> {
        val query = buildQuery(filter)
        
        // NOTE: MongoDB의 Java Stream을 Kotlin Sequence로 변환
        val javaStream: Stream<T> = mongoTemplate.stream(query, entityClass.java)
        activeStreams.add(javaStream) // Tracking for closure
        
        return javaStream.toKotlinSequence()
    }

    /**
     * 리소스 해제 시 추적 중인 모든 스트림을 닫음
     */
    override fun close() {
        activeStreams.forEach { 
            try {
                it.close()
            } catch (e: Exception) {
                // Ignore or log error during closure
            }
        }
        activeStreams.clear()
    }

    /**
     * 필터 맵을 MongoDB Query로 변환
     * Convert filter map to MongoDB Query
     */
    private fun buildQuery(filter: Map<String, Any>): Query {
        if (filter.isEmpty()) {
            return Query()
        }

        val criteria = filter.entries.map { (key, value) ->
            Criteria.where(key).`is`(value)
        }

        return Query().apply {
            criteria.forEach { addCriteria(it) }
        }
    }

    companion object {
        /**
         * 팩토리 메서드 / Factory method
         */
        inline fun <reified T : Any> create(mongoTemplate: MongoTemplate): MongoStreamingDataSource<T> {
            return MongoStreamingDataSource(mongoTemplate, T::class)
        }
    }
}

/**
 * Java Stream을 Kotlin Sequence로 변환하는 확장 함수
 * Extension function to convert Java Stream to Kotlin Sequence
 *
 * NOTE: Sequence 사용 후 Stream이 자동으로 닫히지 않으므로 주의가 필요합니다.
 * Be aware that the Stream is NOT automatically closed after the sequence is consumed.
 */
private fun <T : Any> Stream<T>.toKotlinSequence(): Sequence<T> {
    val iterator = this.iterator()
    return generateSequence {
        if (iterator.hasNext()) iterator.next() else null
    }
}
