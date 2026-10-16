package com.example.connects.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.connects.core.database.AppDatabase
import com.example.connects.core.database.dao.FoodEntryDao
import com.example.connects.core.database.dao.FoodItemDao
import com.example.connects.core.database.dao.UserWaistLogDao
import com.example.connects.core.database.dao.UserWeightLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE food_items ADD COLUMN category TEXT NOT NULL DEFAULT 'other'")
            db.execSQL("ALTER TABLE food_items ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_items ADD COLUMN lastUsedAt INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * 2 -> 3：移除膳食纤维字段（food_items.fiberPer100g、food_entries.totalFiber）。
     * 采用「重建表 + 拷数据 + 改名」而非 ALTER TABLE DROP COLUMN，
     * 因为后者需要 SQLite 3.35+，低版本 Android 设备上不可用。
     * 迁移在 onUpgrade 中执行，此时外键约束尚未开启（onOpen 才开），
     * 因此 DROP 父表不会触发 food_entries 的 ON DELETE SET NULL。
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `food_items_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`caloriesPer100g` REAL NOT NULL, `proteinPer100g` REAL NOT NULL, " +
                    "`carbsPer100g` REAL NOT NULL, `fatPer100g` REAL NOT NULL, " +
                    "`sodiumPer100g` REAL NOT NULL, `isCustom` INTEGER NOT NULL, " +
                    "`isFavorite` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `food_items_new` (`id`,`name`,`category`,`caloriesPer100g`," +
                    "`proteinPer100g`,`carbsPer100g`,`fatPer100g`,`sodiumPer100g`," +
                    "`isCustom`,`isFavorite`,`lastUsedAt`,`createdAt`) " +
                    "SELECT `id`,`name`,`category`,`caloriesPer100g`,`proteinPer100g`," +
                    "`carbsPer100g`,`fatPer100g`,`sodiumPer100g`,`isCustom`,`isFavorite`," +
                    "`lastUsedAt`,`createdAt` FROM `food_items`"
            )
            db.execSQL("DROP TABLE `food_items`")
            db.execSQL("ALTER TABLE `food_items_new` RENAME TO `food_items`")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `food_entries_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`foodItemId` INTEGER, `foodName` TEXT NOT NULL, " +
                    "`amountGrams` REAL NOT NULL, `totalCalories` REAL NOT NULL, " +
                    "`totalProtein` REAL NOT NULL, `totalCarbs` REAL NOT NULL, " +
                    "`totalFat` REAL NOT NULL, `sodium` REAL NOT NULL, " +
                    "`mealType` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                    "FOREIGN KEY(`foodItemId`) REFERENCES `food_items`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            db.execSQL(
                "INSERT INTO `food_entries_new` (`id`,`foodItemId`,`foodName`,`amountGrams`," +
                    "`totalCalories`,`totalProtein`,`totalCarbs`,`totalFat`,`sodium`,`mealType`," +
                    "`dateEpochDay`,`createdAt`,`note`) " +
                    "SELECT `id`,`foodItemId`,`foodName`,`amountGrams`,`totalCalories`," +
                    "`totalProtein`,`totalCarbs`,`totalFat`,`sodium`,`mealType`,`dateEpochDay`," +
                    "`createdAt`,`note` FROM `food_entries`"
            )
            db.execSQL("DROP TABLE `food_entries`")
            db.execSQL("ALTER TABLE `food_entries_new` RENAME TO `food_entries`")

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entries_dateEpochDay` ON `food_entries` (`dateEpochDay`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entries_foodItemId` ON `food_entries` (`foodItemId`)")
        }
    }

    /**
     * 3 -> 4：合并同名食物。
     *
     * 旧版每用一次预置食物（米饭、鸡蛋…）就会克隆一条同名自定义副本，于是食物库里
     * 「米饭」这类预置食物用一次就多一条。这里把同名记录收敛成一条——保留 id 最小的
     * （预置食物先插入，id 更小），并把 food_entries 的引用改指到保留的那条。
     * 每条记录自己存了营养快照，所以历史数据展示不受影响。
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE `food_entries` SET `foodItemId` = (" +
                    "SELECT MIN(f.`id`) FROM `food_items` f WHERE f.`name` = (" +
                    "SELECT g.`name` FROM `food_items` g WHERE g.`id` = `food_entries`.`foodItemId`" +
                    ")) " +
                    "WHERE `foodItemId` IS NOT NULL AND `foodItemId` NOT IN (" +
                    "SELECT MIN(`id`) FROM `food_items` GROUP BY `name`)"
            )
            db.execSQL(
                "DELETE FROM `food_items` WHERE `id` NOT IN (" +
                    "SELECT MIN(`id`) FROM `food_items` GROUP BY `name`)"
            )
        }
    }

    /**
     * 4 -> 5：新增腰围记录表，支撑「每 15 天必填一次腰围」和身体状态趋势。
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_waist_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`waistCm` REAL NOT NULL, " +
                    "`dateEpochDay` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
        }
    }

    /**
     * 5 -> 6：体重记录新增「测量时段」列，历史数据统一按晨起空腹计。
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `user_weight_logs` " +
                    "ADD COLUMN `measurementContext` TEXT NOT NULL DEFAULT 'MORNING_FASTING'"
            )
        }
    }

    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "calorie_app.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    @Singleton
    @Provides
    fun provideFoodItemDao(database: AppDatabase): FoodItemDao {
        return database.foodItemDao()
    }

    @Singleton
    @Provides
    fun provideFoodEntryDao(database: AppDatabase): FoodEntryDao {
        return database.foodEntryDao()
    }

    @Singleton
    @Provides
    fun provideUserWeightLogDao(database: AppDatabase): UserWeightLogDao {
        return database.userWeightLogDao()
    }

    @Singleton
    @Provides
    fun provideUserWaistLogDao(database: AppDatabase): UserWaistLogDao {
        return database.userWaistLogDao()
    }
}
