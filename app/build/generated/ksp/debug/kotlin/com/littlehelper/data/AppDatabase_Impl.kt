package com.littlehelper.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _memoryDao: Lazy<MemoryDao> = lazy {
    MemoryDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(6,
        "0c4a5f637f8ad91ee5df51afc04faa51", "778ac7c3ef18651ac1aab577e622e3b1") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `created_at` INTEGER NOT NULL, `raw_text` TEXT NOT NULL, `summary` TEXT NOT NULL, `category` TEXT NOT NULL, `event_date` TEXT, `formatted_date_for_alarm` TEXT, `event_time` TEXT, `is_recurring` INTEGER NOT NULL, `person` TEXT, `person_pinyin` TEXT, `importance_level` TEXT NOT NULL DEFAULT 'normal', `type` TEXT NOT NULL DEFAULT 'general', `done` INTEGER NOT NULL DEFAULT 0)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0c4a5f637f8ad91ee5df51afc04faa51')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `memories`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsMemories: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMemories.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("raw_text", TableInfo.Column("raw_text", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("summary", TableInfo.Column("summary", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("category", TableInfo.Column("category", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("event_date", TableInfo.Column("event_date", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("formatted_date_for_alarm",
            TableInfo.Column("formatted_date_for_alarm", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("event_time", TableInfo.Column("event_time", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("is_recurring", TableInfo.Column("is_recurring", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("person", TableInfo.Column("person", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("person_pinyin", TableInfo.Column("person_pinyin", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("importance_level", TableInfo.Column("importance_level", "TEXT", true,
            0, "'normal'", TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("type", TableInfo.Column("type", "TEXT", true, 0, "'general'",
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMemories.put("done", TableInfo.Column("done", "INTEGER", true, 0, "0",
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMemories: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesMemories: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoMemories: TableInfo = TableInfo("memories", _columnsMemories, _foreignKeysMemories,
            _indicesMemories)
        val _existingMemories: TableInfo = read(connection, "memories")
        if (!_infoMemories.equals(_existingMemories)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |memories(com.littlehelper.data.MemoryRecord).
              | Expected:
              |""".trimMargin() + _infoMemories + """
              |
              | Found:
              |""".trimMargin() + _existingMemories)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "memories")
  }

  public override fun clearAllTables() {
    super.performClear(false, "memories")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(MemoryDao::class, MemoryDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun memoryDao(): MemoryDao = _memoryDao.value
}
