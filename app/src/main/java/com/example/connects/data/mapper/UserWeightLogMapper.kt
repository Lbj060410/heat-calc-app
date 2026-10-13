package com.example.connects.data.mapper

import com.example.connects.core.database.entity.UserWeightLogEntity
import com.example.connects.domain.model.UserWeightLog
import javax.inject.Inject

class UserWeightLogMapper @Inject constructor() {
    fun toDomain(entity: UserWeightLogEntity): UserWeightLog {
        return UserWeightLog(
            id = entity.id,
            weightKg = entity.weightKg,
            dateEpochDay = entity.dateEpochDay,
            createdAt = entity.createdAt
        )
    }

    fun toDomainList(entities: List<UserWeightLogEntity>): List<UserWeightLog> {
        return entities.map { toDomain(it) }
    }
}
