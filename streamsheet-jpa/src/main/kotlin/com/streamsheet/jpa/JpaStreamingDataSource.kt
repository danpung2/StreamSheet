package com.streamsheet.jpa

import com.streamsheet.core.datasource.StreamingDataSource
import jakarta.persistence.EntityManager
import java.util.stream.Stream

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

    private val activeStreams = mutableListOf<Stream<T>>()

    override val sourceName: String
        get() = "JPA:${this::class.simpleName}"

    override fun stream(): Sequence<T> {
        val javaStream = streamProvider.invoke()
        activeStreams.add(javaStream)

        return javaStream.iterator().asSequence().map { entity ->
            if (detachEntities) {
                // NOTE: 영속성 컨텍스트에서 엔티티를 분리하여 메모리 누수 방지
                // This prevents memory issues by detaching processed entities from the persistence context.
                entityManager.detach(entity)
            }
            entity
        }
    }

    override fun close() {
        activeStreams.forEach {
            runCatching { it.close() }
        }
        activeStreams.clear()
        
        if (detachEntities) {
            // 마지막으로 영속성 컨텍스트를 비워줌
            entityManager.clear()
        }
    }
}
