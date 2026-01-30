package com.streamsheet.mongodb

import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exception.DataSourceException
import com.streamsheet.core.exception.ValidationException
import com.streamsheet.mongodb.filter.MongoFilter
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.query.Criteria
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 * MongoDB aggregation pipeline 기반 스트리밍 데이터 소스
 * MongoDB aggregation pipeline based streaming data source
 */
class MongoAggregationStreamingDataSource<S : Any, T : Any>(
    private val mongoTemplate: MongoTemplate,
    private val sourceClass: KClass<S>,
    private val targetClass: KClass<T>,
    private val pipeline: List<AggregationOperation>,
    private val collectionName: String? = null,
) : StreamingDataSource<T> {

    private val logger = LoggerFactory.getLogger(MongoAggregationStreamingDataSource::class.java)

    // NOTE: 생성된 java.util.stream.Stream들을 추적하여 close() 시점에 일괄 해제
    // Track created java.util.stream.Streams and close them on close()
    private val activeStreams = CopyOnWriteArrayList<Stream<T>>()

    override val sourceName: String
        get() = "MongoDBAggregation:${sourceClass.simpleName}->${targetClass.simpleName}"

    override fun stream(): Sequence<T> {
        return stream(emptyMap())
    }

    override fun stream(filter: Map<String, Any>): Sequence<T> {
        logger.debug("Starting MongoDB aggregation stream: source={}, filterKeys={}", sourceName, filter.keys)

        val matchOperation = if (filter.isEmpty()) {
            null
        } else {
            val criteriaList = filter.map { (key, value) ->
                validateFilterKey(key)
                when (value) {
                    is MongoFilter -> value.toCriteria(key)
                    is Map<*, *> -> {
                        logger.warn("Invalid filter value rejected (map): source={}, key={}", sourceName, key)
                        throw ValidationException(
                            "Filter value must not be a map. Use typed filters instead.",
                            fieldName = "filter.value",
                            invalidValue = value
                        )
                    }
                    else -> Criteria.where(key).`is`(value)
                }
            }

            if (criteriaList.isEmpty()) {
                null
            } else {
                val composite = Criteria().andOperator(*criteriaList.toTypedArray())
                Aggregation.match(composite)
            }
        }

        val operations = buildList {
            if (matchOperation != null) add(matchOperation)
            addAll(pipeline)
        }

        val safeOperations = if (operations.isEmpty()) {
            // NOTE: Spring Data는 AggregationOperation이 최소 1개 필요하므로 no-op match를 사용합니다.
            // Spring Data requires at least one operation, so we use an empty match as a no-op stage.
            listOf(Aggregation.match(Criteria()))
        } else {
            operations
        }

        val aggregation = Aggregation.newAggregation(safeOperations)
        val resolvedCollectionName = collectionName ?: mongoTemplate.getCollectionName(sourceClass.java)

        val javaStream: Stream<T> = try {
            mongoTemplate.aggregateStream(aggregation, resolvedCollectionName, targetClass.java)
        } catch (e: Exception) {
            logger.error(
                "Failed to create MongoDB aggregation stream: source={}, collection={}, error={}",
                sourceName,
                resolvedCollectionName,
                e.message,
                e
            )
            throw DataSourceException("Failed to execute MongoDB aggregation", sourceName, e)
        }

        return wrapStream(javaStream)
    }

    private fun wrapStream(javaStream: Stream<T>): Sequence<T> {
        javaStream.onClose {
            activeStreams.remove(javaStream)
            logger.debug("MongoDB aggregation stream closed: source={}", sourceName)
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
            logger.debug("Closing {} active MongoDB aggregation stream(s): source={}", streamCount, sourceName)
        }

        val errors = mutableListOf<Throwable>()
        activeStreams.toList().forEach { stream ->
            runCatching { stream.close() }
                .onFailure { e ->
                    logger.warn("Failed to close MongoDB aggregation stream: source={}, error={}", sourceName, e.message, e)
                    errors.add(e)
                }
        }
        activeStreams.clear()

        if (errors.isNotEmpty()) {
            logger.warn("Completed closing aggregation streams with {} error(s): source={}", errors.size, sourceName)
        } else if (streamCount > 0) {
            logger.debug("Successfully closed all aggregation streams: source={}", sourceName)
        }
    }

    private fun validateFilterKey(key: String) {
        if (!key.matches(Regex("^[a-zA-Z0-9._]+$"))) {
            logger.warn("Invalid filter key rejected: source={}, key={}", sourceName, key)
            throw ValidationException(
                "Filter key must contain only alphanumeric characters, dots, and underscores. Pattern: ^[a-zA-Z0-9._]+\$",
                fieldName = "filter.key",
                invalidValue = key
            )
        }
    }

    companion object {
        @JvmStatic
        fun <T : Any> create(
            mongoTemplate: MongoTemplate,
            targetClass: Class<T>,
            pipeline: List<AggregationOperation>,
            collectionName: String? = null,
        ): MongoAggregationStreamingDataSource<T, T> {
            return MongoAggregationStreamingDataSource(
                mongoTemplate = mongoTemplate,
                sourceClass = targetClass.kotlin,
                targetClass = targetClass.kotlin,
                pipeline = pipeline,
                collectionName = collectionName,
            )
        }

        @JvmStatic
        fun <S : Any, T : Any> createWithProjection(
            mongoTemplate: MongoTemplate,
            sourceClass: Class<S>,
            targetClass: Class<T>,
            pipeline: List<AggregationOperation>,
            collectionName: String? = null,
        ): MongoAggregationStreamingDataSource<S, T> {
            return MongoAggregationStreamingDataSource(
                mongoTemplate = mongoTemplate,
                sourceClass = sourceClass.kotlin,
                targetClass = targetClass.kotlin,
                pipeline = pipeline,
                collectionName = collectionName,
            )
        }

        inline fun <reified T : Any> create(
            mongoTemplate: MongoTemplate,
            pipeline: List<AggregationOperation>,
            collectionName: String? = null,
        ): MongoAggregationStreamingDataSource<T, T> {
            return MongoAggregationStreamingDataSource(
                mongoTemplate = mongoTemplate,
                sourceClass = T::class,
                targetClass = T::class,
                pipeline = pipeline,
                collectionName = collectionName,
            )
        }

        inline fun <reified S : Any, reified T : Any> createWithProjection(
            mongoTemplate: MongoTemplate,
            pipeline: List<AggregationOperation>,
            collectionName: String? = null,
        ): MongoAggregationStreamingDataSource<S, T> {
            return MongoAggregationStreamingDataSource(
                mongoTemplate = mongoTemplate,
                sourceClass = S::class,
                targetClass = T::class,
                pipeline = pipeline,
                collectionName = collectionName,
            )
        }
    }
}
