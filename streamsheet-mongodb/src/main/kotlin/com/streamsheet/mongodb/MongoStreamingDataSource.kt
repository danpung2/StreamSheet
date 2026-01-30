package com.streamsheet.mongodb

import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exception.DataSourceException
import com.streamsheet.core.exception.ValidationException
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(MongoStreamingDataSource::class.java)

    // NOTE: 생성된 java.util.stream.Stream들을 추적하여 close() 시점에 일괄 해제
    private val activeStreams = CopyOnWriteArrayList<Stream<T>>()

    override val sourceName: String
        get() = "MongoDB:${sourceClass.simpleName}->${targetClass.simpleName}"

    override fun stream(): Sequence<T> {
        logger.debug("Starting MongoDB stream: source={}", sourceName)

        val javaStream: Stream<T> = try {
            mongoTemplate.query(sourceClass.java)
                .`as`(targetClass.java)
                .matching(query)
                .stream()
        } catch (e: Exception) {
            logger.error("Failed to create MongoDB stream: source={}, error={}", sourceName, e.message, e)
            throw DataSourceException("Failed to execute MongoDB query", sourceName, e)
        }

        return wrapStream(javaStream)
    }

    override fun stream(filter: Map<String, Any>): Sequence<T> {
        logger.debug("Starting MongoDB stream with filter: source={}, filterKeys={}", sourceName, filter.keys)

        val compositeQuery = Query.of(query)
        filter.forEach { (key, value) ->
            // NOTE: NoSQL Injection 방지를 위해 Key 값에 특수 문자가 포함되어 있는지 검증합니다.
            // Validate key to prevent NoSQL injection (Allow only alphanumeric and dots)
            if (!key.matches(Regex("^[a-zA-Z0-9._]+$"))) {
                logger.warn("Invalid filter key rejected: source={}, key={}", sourceName, key)
                throw ValidationException(
                    "Filter key must contain only alphanumeric characters, dots, and underscores. Pattern: ^[a-zA-Z0-9._]+\$",
                    fieldName = "filter.key",
                    invalidValue = key
                )
            }
            compositeQuery.addCriteria(Criteria.where(key).`is`(value))
        }

        val javaStream: Stream<T> = try {
            mongoTemplate.query(sourceClass.java)
                .`as`(targetClass.java)
                .matching(compositeQuery)
                .stream()
        } catch (e: Exception) {
            logger.error("Failed to create filtered MongoDB stream: source={}, error={}", sourceName, e.message, e)
            throw DataSourceException("Failed to execute filtered MongoDB query", sourceName, e)
        }

        return wrapStream(javaStream)
    }

    private fun wrapStream(javaStream: Stream<T>): Sequence<T> {
        javaStream.onClose {
            activeStreams.remove(javaStream)
            logger.debug("MongoDB stream closed: source={}", sourceName)
        }
        activeStreams.add(javaStream)

        return Sequence {
            val iterator = javaStream.iterator()
            object : Iterator<T> {
                override fun hasNext(): Boolean {
                    val hasNext = iterator.hasNext()
                    if (!hasNext) {
                        javaStream.close()
                    }
                    return hasNext
                }

                override fun next(): T = iterator.next()
            }
        }
    }

    override fun close() {
        val streamCount = activeStreams.size
        if (streamCount > 0) {
            logger.debug("Closing {} active MongoDB stream(s): source={}", streamCount, sourceName)
        }

        val errors = mutableListOf<Throwable>()
        activeStreams.toList().forEach { stream ->
            runCatching { stream.close() }
                .onFailure { e ->
                    logger.warn("Failed to close MongoDB stream: source={}, error={}", sourceName, e.message, e)
                    errors.add(e)
                }
        }
        activeStreams.clear()

        if (errors.isNotEmpty()) {
            logger.warn("Completed closing with {} error(s): source={}", errors.size, sourceName)
        } else if (streamCount > 0) {
            logger.debug("Successfully closed all MongoDB streams: source={}", sourceName)
        }
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
