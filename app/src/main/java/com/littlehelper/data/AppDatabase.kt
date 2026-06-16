package com.littlehelper.data



import android.content.Context

import androidx.room.Database

import androidx.room.Room

import androidx.room.RoomDatabase

import androidx.room.migration.Migration

import androidx.sqlite.db.SupportSQLiteDatabase



@Database(

    entities = [MemoryRecord::class],

    version = 6,

    exportSchema = false

)

abstract class AppDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao



    companion object {

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE memories ADD COLUMN importance_level TEXT NOT NULL DEFAULT 'normal'"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE memories ADD COLUMN type TEXT NOT NULL DEFAULT 'general'"
                )
                db.execSQL(
                    "ALTER TABLE memories ADD COLUMN done INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile

        private var instance: AppDatabase? = null



        fun getInstance(context: Context): AppDatabase {

            return instance ?: synchronized(this) {

                instance ?: Room.databaseBuilder(

                    context.applicationContext,

                    AppDatabase::class.java,

                    "little_helper.db"

                )

                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)

                    .fallbackToDestructiveMigration(dropAllTables = true)

                    .build()

                    .also { instance = it }

            }

        }

    }

}


