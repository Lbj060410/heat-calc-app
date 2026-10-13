package com.example.connects.data.mapper

import com.example.connects.core.database.entity.UserWaistLogEntity
import com.example.connects.domain.model.UserWaistLog
import javax.inject.Inject

class UserWaistLogMapper @Inject constructor() {
    fun toDomain(entity: UserWaistLogEntity): UserWaistLog {
        return UserWaistLog(
            id = entity.id,
            waistCm = entity.waistCm,
            dateEpochDay = entity.dateEpochDay,
            createdAt = entity.createdAt
        )
    }

    fun toDomainOrNull(entity: UserWaistLogEntity?): UserWaistLog? {
        return entity?.let { toDomain(it) }
    }
}