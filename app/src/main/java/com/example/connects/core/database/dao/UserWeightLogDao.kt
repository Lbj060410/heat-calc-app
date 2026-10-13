package com.example.connects.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.connects.core.database.entity.UserWeightLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserWeightLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: UserWeightLogEntity)

    @Update
    suspend fun update(log: UserWeightLogEntity)

    @Query("SELECT * FROM user_weight_logs WHERE dateEpochDay = :dateEpochDay")
    suspend fun getByDate(dateEpochDay: Int): UserWeightLogEntity?

    /** 当天是否已记录过体重，用于「每天首次进入」的弹窗判断。写成 Flow 以便落库后自动收起弹窗。 */
    @Query("SELECT EXISTS(SELECT 1 FROM user_weight_logs WHERE dateEpochDay = :dateEpochDay)")
    fun observeHasLogForDate(dateEpochDay: Int): Flow<Boolean>

    /** 窗口内的全部体重记录（按日期升序），用于「身体状态」的趋势判断。 */
    @Query(
        "SELECT * FROM user_weight_logs " +
            "WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY dateEpochDay ASC"
    )
    fun getLogsBetween(startEpochDay: Int, endEpochDay: Int): Flow<List<UserWeightLogEntity>>

    /** 最早一条体重记录的日期，用于给老用户回填「正式记录的第一天」。 */
    @Query("SELECT MIN(dateEpochDay) FROM user_weight_logs")
    fun observeEarliestDate(): Flow<Int?>

    @Delete
    suspend fun delete(log: UserWeightLogEntity)

    @Query("DELETE FROM user_weight_logs WHERE dateEpochDay < :beforeEpochDay")
    suspend fun deleteOldLogs(beforeEpochDay: Int)
}
