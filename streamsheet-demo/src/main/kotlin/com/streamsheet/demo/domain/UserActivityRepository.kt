package com.streamsheet.demo.domain

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserActivityRepository : MongoRepository<UserActivityDocument, String>
