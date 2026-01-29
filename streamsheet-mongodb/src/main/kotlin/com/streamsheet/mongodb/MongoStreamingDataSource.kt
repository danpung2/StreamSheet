package com.streamsheet.mongodb

import com.streamsheet.core.datasource.StreamingDataSource
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.stream.Stream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * MongoDB 기반 스트리밍 데이터 소스
 * MongoDB-based streaming data source
 */
class MongoStreamingDataSource<S : Any, T : Any>(
    private val mongoTemplate: MongoTemplate,
    private val sourceClass: KClass<S>,
    private val targetClass: KClass<T>,
    private val query: Query = Query()
) : StreamingDataSource<T> {

    // NOTE: 생성된 java.util.stream.Stream들을 추적하여 close() 시점에 일괄 해제
    private val activeStreams = CopyOnWriteArrayList<Stream<T>>()

    override val sourceName: String
        get() = "MongoDB:${sourceClass.simpleName}->${targetClass.simpleName}"

    override fun stream(): Sequence<T> {
        val javaStream: Stream<T> = mongoTemplate.query(sourceClass.java)
            .`as`(targetClass.java)
            .matching(query)
            .stream()
            
        javaStream.onClose { activeStreams.remove(javaStream) }
        activeStreams.add(javaStream)
        
        return javaStream.iterator().asSequence()
    }

    override fun stream(filter: Map<String, Any>): Sequence<T> {
        val compositeQuery = Query.of(query)
        filter.forEach { (key, value) ->
            // NOTE: NoSQL Injection 방지를 위해 Key 값에 특수 문자가 포함되어 있는지 검증합니다.
            // Validate key to prevent NoSQL injection (Allow only alphanumeric and dots)
            require(key.matches(Regex("^[a-zA-Z0-9._]+$"))) { "Invalid filter key: $key" }
            compositeQuery.addCriteria(Criteria.where(key).`is`(value))
        }
        
        val javaStream: Stream<T> = mongoTemplate.query(sourceClass.java)
            .`as`(targetClass.java)
            .matching(compositeQuery)
            .stream()
            
        javaStream.onClose { activeStreams.remove(javaStream) }
        activeStreams.add(javaStream)
        
        return javaStream.iterator().asSequence()
    }

    override fun close() {
        activeStreams.toList().forEach { 
            runCatching { it.close() }
        }
        activeStreams.clear()
    }

    companion object {
        inline fun <reified T : Any> create(mongoTemplate: MongoTemplate, query: Query = Query()): MongoStreamingDataSource<T, T> {
            return MongoStreamingDataSource(mongoTemplate, T::class, T::class, query)
        }

        inline fun <reified S : Any, reified T : Any> createWithProjection(
            mongoTemplate: MongoTemplate, 
            query: Query = Query()
        ): MongoStreamingDataSource<S, T> {
            return MongoStreamingDataSource(mongoTemplate, S::class, T::class, query)
        }
    }
}
