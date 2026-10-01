package com.sumpilot.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        CalculationEntity::class,
        SessionEntity::class,
        QuestionEntity::class,
        MistakeEntity::class,
        MistakeSourceEntity::class,
        BadgeEntity::class,
        ProgressTotalsEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calculationDao(): CalculationDao
    abstract fun sessionDao(): SessionDao
    abstract fun questionDao(): QuestionDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun progressDao(): ProgressDao

    companion object {
        const val VERSION = 1
        const val NAME = "sumpilot.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // No destructive fallback: a missing migration must fail loudly in testing
                // rather than silently wiping a child's practice history.
                .build()
    }
}

/**
 * Schema migrations. Version 1 is the initial schema (exported to app/schemas/).
 * Every future schema change must bump [AppDatabase.VERSION] and add a Migration here,
 * e.g. `val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { ... } }`.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
