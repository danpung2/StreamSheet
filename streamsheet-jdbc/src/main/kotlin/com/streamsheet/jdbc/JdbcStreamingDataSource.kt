package com.streamsheet.jdbc

import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exception.DataSourceException
import com.streamsheet.core.exception.ResourceCleanupException
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Stream

/**
 * JDBC ResultSet 기반 스트리밍 데이터 소스
 * JDBC ResultSet-based streaming data source
 */
class JdbcStreamingDataSource<T>(
    private val jdbcTemplate: JdbcTemplate,
    private val sql: String,
    private val rowMapper: RowMapper<T>,
    private val params: Map<String, Any?>? = null,
    fetchSize: Int? = null,
    queryTimeout: Int? = null
) : StreamingDataSource<T> {

    private val logger = LoggerFactory.getLogger(JdbcStreamingDataSource::class.java)
    private val activeStreams = CopyOnWriteArrayList<Stream<T>>()
    
    // NOTE: 공유 JdbcTemplate의 설정을 건드리지 않기 위해 
    // fetchSize 또는 queryTimeout이 지정된 경우 로컬 래퍼를 생성하여 사용합니다. (Thread-safe)
    private val targetJdbcTemplate: JdbcTemplate = if (fetchSize != null || queryTimeout != null) {
        val dataSource = requireNotNull(jdbcTemplate.dataSource) { "JdbcTemplate must have a valid DataSource" }
        JdbcTemplate(dataSource).apply {
            if (fetchSize != null) {
                this.fetchSize = fetchSize
            }
            if (queryTimeout != null) {
                this.queryTimeout = queryTimeout
            }
        }
    } else {
        jdbcTemplate
    }

    private val namedTemplate = NamedParameterJdbcTemplate(targetJdbcTemplate)

    override val sourceName: String
        get() = "JDBC:${sql.trim().take(30)}..."

    override fun stream(): Sequence<T> {
        logger.debug("Starting JDBC stream: source={}, sql={}", sourceName, sql.take(50))

        val javaStream = try {
            if (params != null) {
                namedTemplate.queryForStream(sql, params, rowMapper)
            } else {
                targetJdbcTemplate.queryForStream(sql, rowMapper)
            }
        } catch (e: Exception) {
            logger.error("Failed to create JDBC stream: source={}, error={}", sourceName, e.message, e)
            throw DataSourceException("Failed to execute query", sourceName, e)
        }

        // 리소스 누수 방지를 위한 핸들러
        javaStream.onClose {
            activeStreams.remove(javaStream)
            logger.debug("JDBC stream closed: source={}", sourceName)
        }
        activeStreams.add(javaStream)

        // Sequence가 소진될 때 자동으로 Stream을 닫도록 구성
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
            logger.debug("Closing {} active JDBC stream(s): source={}", streamCount, sourceName)
        }

        val errors = mutableListOf<Throwable>()
        activeStreams.toList().forEach { stream ->
            runCatching { stream.close() }
                .onFailure { e ->
                    logger.warn("Failed to close JDBC stream: source={}, error={}", sourceName, e.message, e)
                    errors.add(e)
                }
        }
        activeStreams.clear()

        if (errors.isNotEmpty()) {
            logger.warn("Completed closing with {} error(s): source={}", errors.size, sourceName)
        } else if (streamCount > 0) {
            logger.debug("Successfully closed all JDBC streams: source={}", sourceName)
        }
    }
}
