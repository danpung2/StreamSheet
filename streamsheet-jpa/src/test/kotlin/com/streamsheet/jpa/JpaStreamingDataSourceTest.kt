package com.streamsheet.jpa

import jakarta.persistence.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.stream.Stream

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
    fun `JPA 스트림 데이터 소스 테스트`() {
        // Given
        for (i in 1..100) {
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
        assertEquals(100, result.size)
        assertEquals("Data-1", result[0].name)
        
        // Detach 검증: 영속성 컨텍스트에 포함되어 있지 않아야 함
        val firstEntity = result[0]
        assertEquals(false, entityManager.contains(firstEntity))
        
        dataSource.close()
    }
}
