package com.streamsheet.jdbc

import com.streamsheet.core.datasource.StreamingDataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.stream.Stream
import java.util.Collections

/**
 * JDBC ResultSet 기반 스트리밍 데이터 소스
 * JDBC ResultSet-based streaming data source
 */
class JdbcStreamingDataSource<T>(
    private val jdbcTemplate: JdbcTemplate,
    private val sql: String,
    private val rowMapper: RowMapper<T>,
    private val params: Map<String, Any?>? = null,
    fetchSize: Int? = null
) : StreamingDataSource<T> {

    private val activeStreams = Collections.synchronizedList(mutableListOf<Stream<T>>())
    
    // NOTE: 공유 JdbcTemplate의 설정을 건드리지 않기 위해 
    // fetchSize가 지정된 경우 로컬 래퍼를 생성하여 사용합니다. (Thread-safe)
    private val targetJdbcTemplate = if (fetchSize != null) {
        val dataSource = requireNotNull(jdbcTemplate.dataSource) { "JdbcTemplate must have a valid DataSource" }
        JdbcTemplate(dataSource).apply {
            this.fetchSize = fetchSize
        }
    } else {
        jdbcTemplate
    }

    private val namedTemplate = NamedParameterJdbcTemplate(targetJdbcTemplate)

    override val sourceName: String
        get() = "JDBC:${sql.trim().take(30)}..."

    override fun stream(): Sequence<T> {
        val javaStream = if (params != null) {
            namedTemplate.queryForStream(sql, params, rowMapper)
        } else {
            targetJdbcTemplate.queryForStream(sql, rowMapper)
        }
        
        javaStream.onClose { activeStreams.remove(javaStream) }
        activeStreams.add(javaStream)
        
        return javaStream.iterator().asSequence()
    }

    override fun close() {
        activeStreams.forEach { 
            runCatching { it.close() }
        }
        activeStreams.clear()
    }
}
