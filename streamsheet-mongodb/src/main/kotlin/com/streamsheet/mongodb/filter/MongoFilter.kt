package com.streamsheet.mongodb.filter

import com.streamsheet.core.exception.ValidationException
import org.springframework.data.mongodb.core.query.Criteria

sealed interface MongoFilter {
    fun toCriteria(field: String): Criteria

    data class Eq(val value: Any?) : MongoFilter {
        override fun toCriteria(field: String): Criteria {
            return Criteria.where(field).`is`(value)
        }
    }

    data class In(val values: List<Any?>) : MongoFilter {
        override fun toCriteria(field: String): Criteria {
            if (values.isEmpty()) {
                throw ValidationException("IN filter values must not be empty", fieldName = "filter.value")
            }
            if (values.any { it is Map<*, *> }) {
                throw ValidationException("IN filter values must not contain maps", fieldName = "filter.value")
            }
            if (values.size > 1000) {
                throw ValidationException("IN filter values size must be <= 1000", fieldName = "filter.value")
            }
            return Criteria.where(field).`in`(values)
        }
    }

    data class Range(
        val gt: Any? = null,
        val gte: Any? = null,
        val lt: Any? = null,
        val lte: Any? = null,
    ) : MongoFilter {
        override fun toCriteria(field: String): Criteria {
            if (gt == null && gte == null && lt == null && lte == null) {
                throw ValidationException("Range filter must have at least one bound", fieldName = "filter.value")
            }
            if (gt is Map<*, *> || gte is Map<*, *> || lt is Map<*, *> || lte is Map<*, *>) {
                throw ValidationException("Range bounds must not be maps", fieldName = "filter.value")
            }

            var criteria = Criteria.where(field)
            if (gt != null) criteria = criteria.gt(gt)
            if (gte != null) criteria = criteria.gte(gte)
            if (lt != null) criteria = criteria.lt(lt)
            if (lte != null) criteria = criteria.lte(lte)
            return criteria
        }
    }

    data class Regex(
        val pattern: String,
        val options: String? = null,
    ) : MongoFilter {
        override fun toCriteria(field: String): Criteria {
            if (pattern.isBlank()) {
                throw ValidationException("Regex pattern must not be blank", fieldName = "filter.value")
            }
            if (pattern.length > 256) {
                throw ValidationException("Regex pattern length must be <= 256", fieldName = "filter.value")
            }

            return if (options.isNullOrBlank()) {
                Criteria.where(field).regex(pattern)
            } else {
                Criteria.where(field).regex(pattern, options)
            }
        }
    }
}
