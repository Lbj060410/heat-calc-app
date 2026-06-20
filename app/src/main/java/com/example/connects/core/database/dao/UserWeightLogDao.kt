package com.example.connects.core.database.dao

import androidx.room.*
import com.example.connects.core.database.entity.UserWeightLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserWeightLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: UserWeightLogEntity)

    @Update
    suspend fun update(log: UserWeightLogEntity)

    @Query("SELECT * FROM user_weight_logs ORDER BY dateEpochDay DESC LIMIT 30")
    fun getRecentLogs(): Flow<List<UserWeightLogEntity>>

    @Query("SELECT * FROM user_weight_logs ORDER BY dateEpochDay ASC")
    fun getAllLogsAsc(): Flow<List<UserWeightLogEntity>>

    @Query("SELECT * FROM user_weight_logs WHERE dateEpochDay = :dateEpochDay")
    suspend fun getByDate(dateEpochDay: Int): UserWeightLogEntity?

    @Delete
    suspend fun delete(log: UserWeightLogEntity)

    @Query("DELETE FROM user_weight_logs WHERE dateEpochDay < :beforeEpochDay")
    suspend fun deleteOldLogs(beforeEpochDay: Int)
}
