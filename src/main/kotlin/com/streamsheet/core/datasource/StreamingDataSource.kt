package com.streamsheet.core.datasource

/**
 * 스트리밍 데이터 소스 인터페이스
 * Streaming data source interface
 *
 * NOTE: 대용량 데이터를 메모리에 전부 로드하지 않고 스트리밍 방식으로 제공합니다.
 * 리소스 누수를 방지하기 위해 AutoCloseable을 상속합니다.
 * Provides large-scale data in a streaming manner without loading all into memory.
 * Inherits AutoCloseable to prevent resource leaks.
 *
 * @param T 스트리밍할 엔티티 타입 / Entity type to stream
 */
interface StreamingDataSource<T> : AutoCloseable {
    /**
     * 전체 데이터를 스트리밍으로 반환
     * Returns all data as a stream
     *
     * @return 데이터 시퀀스 / Data sequence
     */
    fun stream(): Sequence<T>

    /**
     * 필터 조건에 맞는 데이터를 스트리밍으로 반환
     * Returns filtered data as a stream
     *
     * @param filter 필터 조건 (키-값 쌍) / Filter conditions (key-value pairs)
     * @return 필터링된 데이터 시퀀스 / Filtered data sequence
     */
    fun stream(filter: Map<String, Any>): Sequence<T> = stream()

    /**
     * 리소스 해제
     * Release resources
     */
    override fun close()

    /**
     * 데이터 소스 이름 (로깅/디버깅용)
     * Data source name (for logging/debugging)
     */
    val sourceName: String
        get() = this::class.simpleName ?: "UnknownDataSource"
}
