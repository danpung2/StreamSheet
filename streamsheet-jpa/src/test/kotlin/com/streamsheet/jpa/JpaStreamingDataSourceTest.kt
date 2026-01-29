package com.streamsheet.jpa

import jakarta.persistence.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.stream.Stream
import java.lang.reflect.Field

@Entity
@Table(name = "jpa_test_data")
data class JpaTestData(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val name: String
)

@Repository
interface JpaTestDataRepository : JpaRepository<JpaTestData, Long> {
    @Query("SELECT j FROM JpaTestData j")
    fun streamAll(): Stream<JpaTestData>
}

@DataJpaTest
class JpaStreamingDataSourceTest {

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var repository: JpaTestDataRepository

    @Test
    fun `JPA 스트림 데이터 소스 테스트 - detach true`() {
        // Given
        for (i in 1..10) {
            repository.save(JpaTestData(name = "Data-$i"))
        }
        entityManager.flush()
        entityManager.clear()

        // When
        val dataSource = JpaStreamingDataSource(
            entityManager = entityManager,
            streamProvider = { repository.streamAll() },
            detachEntities = true
        )

        val result = dataSource.stream().toList()

        // Then
        assertEquals(10, result.size)
        // Detach 검증: 영속성 컨텍스트에 포함되어 있지 않아야 함
        assertFalse(entityManager.contains(result[0]))
        
        dataSource.close()
    }

    @Test
    fun `JPA 스트림 데이터 소스 테스트 - detach false`() {
        // Given
        for (i in 1..5) {
            repository.save(JpaTestData(name = "Data-$i"))
        }
        entityManager.flush()

        // When
        val dataSource = JpaStreamingDataSource(
            entityManager = entityManager,
            streamProvider = { repository.streamAll() },
            detachEntities = false
        )

        val result = dataSource.stream().toList()

        // Then
        assertEquals(5, result.size)
        dataSource.close()
    }

    @Test
    fun `close 호출 시 리소스가 해제되어야 한다`() {
        // Given
        val dataSource = JpaStreamingDataSource(
            entityManager = entityManager,
            streamProvider = { repository.streamAll() }
        )
        
        dataSource.stream().toList()
        
        val field: Field = dataSource.javaClass.getDeclaredField("activeStreams")
        field.isAccessible = true
        val activeStreams = field.get(dataSource) as List<*>
        
        // When
        dataSource.close()
        
        // Then
        assertEquals(0, activeStreams.size)
    }
}
