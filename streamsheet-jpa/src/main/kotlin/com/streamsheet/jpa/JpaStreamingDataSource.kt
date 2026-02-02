package com.streamsheet.jpa

import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exception.DataSourceException
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import java.util.stream.Stream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JPA 엔티티 기반 스트리밍 데이터 소스
 * JPA Entity-based streaming data source
 *
 * **중요 / Important**:
 * 1. **트랜잭션 필수 (Active Transaction Required)**:
 *    이 데이터 소스는 DB 커서를 유지하는 `Stream`을 사용하므로, 호출하는 스레드에 활성 트랜잭션이 있어야 합니다.
 *    스트림이 닫힐 때까지 DB 연결을 붙잡고 있어야 하므로 `@Transactional(readOnly = true)`가 권장됩니다.
 *    This data source uses a `Stream` that holds a DB cursor, so an active transaction is required in the calling thread.
 *    `@Transactional(readOnly = true)` is recommended as the DB connection must be held until the stream is closed.
 *
 * 2. **지연 로딩 주의 (Lazy Loading Caveat)**:
 *    [detachEntities]가 true(기본값)인 경우, 영속성 컨텍스트 크기 증가를 막기 위해 소비된 엔티티를 즉시 준영속 상태로 만듭니다.
 *    이 상태에서 초기화되지 않은 지연 로딩 프로퍼티에 접근하면 `LazyInitializationException`이 발생합니다.
 *    필요한 연관 관계는 `JOIN FETCH` 등으로 미리 로딩해야 합니다.
 *    If [detachEntities] is true (default), consumed entities are immediately detached to prevent persistence context bloat.
 *    Accessing uninitialized lazy-loaded properties in this state will cause `LazyInitializationException`.
 *    Required associations must be pre-loaded, e.g., using `JOIN FETCH`.
 *
 * @param T 스트리밍할 엔티티 타입 / Entity type to stream
 * @param entityManager JPA EntityManager
 * @param streamProvider 스트림 생성 함수 / Stream provider function
 * @param detachEntities 각 행 처리 후 영속성 컨텍스트에서 분리 여부 / Whether to detach entities from persistence context after processing
 */
class JpaStreamingDataSource<T : Any>(
    private val entityManager: EntityManager,
    private val streamProvider: () -> Stream<T>,
    private val detachEntities: Boolean = true
) : StreamingDataSource<T> {

    private val logger = LoggerFactory.getLogger(JpaStreamingDataSource::class.java)
    private val activeStreams = CopyOnWriteArrayList<Stream<T>>()

    override val sourceName: String
        get() = "JPA:${this::class.simpleName}"

    override fun stream(): Sequence<T> {
        logger.debug("Starting JPA stream: source={}, detachEntities={}", sourceName, detachEntities)

        val javaStream = try {
            streamProvider.invoke()
        } catch (e: Exception) {
            logger.error("Failed to create JPA stream: source={}, error={}", sourceName, e.message, e)
            throw DataSourceException("Failed to create entity stream", sourceName, e)
        }

        javaStream.onClose {
            activeStreams.remove(javaStream)
            logger.debug("JPA stream closed: source={}", sourceName)
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

                override fun next(): T {
                    val entity = iterator.next()
                    if (detachEntities) {
                        entityManager.detach(entity)
                    }
                    return entity
                }
            }
        }
    }

    override fun close() {
        val streamCount = activeStreams.size
        if (streamCount > 0) {
            logger.debug("Closing {} active JPA stream(s): source={}", streamCount, sourceName)
        }

        val errors = mutableListOf<Throwable>()
        activeStreams.toList().forEach { stream ->
            runCatching { stream.close() }
                .onFailure { e ->
                    logger.warn("Failed to close JPA stream: source={}, error={}", sourceName, e.message, e)
                    errors.add(e)
                }
        }
        activeStreams.clear()

        if (detachEntities) {
            // 마지막으로 영속성 컨텍스트를 비워줌
            logger.debug("Clearing EntityManager persistence context: source={}", sourceName)
            runCatching { entityManager.clear() }
                .onFailure { e ->
                    logger.warn("Failed to clear EntityManager: source={}, error={}", sourceName, e.message, e)
                }
        }

        if (errors.isNotEmpty()) {
            logger.warn("Completed closing with {} error(s): source={}", errors.size, sourceName)
        } else if (streamCount > 0) {
            logger.debug("Successfully closed all JPA streams: source={}", sourceName)
        }
    }
}
