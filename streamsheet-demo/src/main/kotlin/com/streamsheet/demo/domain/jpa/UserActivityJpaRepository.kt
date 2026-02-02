package com.streamsheet.demo.domain.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserActivityJpaRepository : JpaRepository<UserActivityEntity, String>
