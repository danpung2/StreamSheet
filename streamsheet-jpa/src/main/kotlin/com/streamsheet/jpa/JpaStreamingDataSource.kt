package com.streamsheet.jpa

import com.streamsheet.core.datasource.StreamingDataSource
import jakarta.persistence.EntityManager
import java.util.stream.Stream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JPA 엔티티 기반 스트리밍 데이터 소스
 * JPA Entity-based streaming data source
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

    private val activeStreams = CopyOnWriteArrayList<Stream<T>>()

    override val sourceName: String
        get() = "JPA:${this::class.simpleName}"

    override fun stream(): Sequence<T> {
        val javaStream = streamProvider.invoke()
        javaStream.onClose { activeStreams.remove(javaStream) }
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
        activeStreams.toList().forEach {
            runCatching { it.close() }
        }
        activeStreams.clear()
        
        if (detachEntities) {
            // 마지막으로 영속성 컨텍스트를 비워줌
            entityManager.clear()
        }
    }
}
