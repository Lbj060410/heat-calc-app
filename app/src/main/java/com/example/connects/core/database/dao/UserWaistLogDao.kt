package com.example.connects.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.connects.core.database.entity.UserWaistLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserWaistLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: UserWaistLogEntity)

    @Update
    suspend fun update(log: UserWaistLogEntity)

    @Query("SELECT * FROM user_waist_logs WHERE dateEpochDay = :dateEpochDay")
    suspend fun getByDate(dateEpochDay: Int): UserWaistLogEntity?

    /** 最近一次腰围记录，用于「每 15 天必填一次」的到期判断。 */
    @Query("SELECT * FROM user_waist_logs ORDER BY dateEpochDay DESC LIMIT 1")
    fun observeLatest(): Flow<UserWaistLogEntity?>

    /** 周期内的最后一次腰围记录，代表这个 15 天的期末值。 */
    @Query(
        "SELECT * FROM user_waist_logs " +
            "WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY dateEpochDay DESC LIMIT 1"
    )
    fun observeLatestInRange(
        startEpochDay: Int,
        endEpochDay: Int
    ): Flow<UserWaistLogEntity?>

    /** 周期开始之前的最后一次腰围记录，作为这个 15 天的期初基线。 */
    @Query(
        "SELECT * FROM user_waist_logs " +
            "WHERE dateEpochDay < :dateEpochDay " +
            "ORDER BY dateEpochDay DESC LIMIT 1"
    )
    fun observeLatestBefore(dateEpochDay: Int): Flow<UserWaistLogEntity?>
}