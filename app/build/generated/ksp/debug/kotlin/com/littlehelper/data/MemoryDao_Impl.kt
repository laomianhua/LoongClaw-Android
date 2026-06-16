package com.littlehelper.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.getTotalChangedRows
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class MemoryDao_Impl(
  __db: RoomDatabase,
) : MemoryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMemoryRecord: EntityInsertAdapter<MemoryRecord>

  private val __deleteAdapterOfMemoryRecord: EntityDeleteOrUpdateAdapter<MemoryRecord>

  private val __updateAdapterOfMemoryRecord: EntityDeleteOrUpdateAdapter<MemoryRecord>
  init {
    this.__db = __db
    this.__insertAdapterOfMemoryRecord = object : EntityInsertAdapter<MemoryRecord>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `memories` (`id`,`created_at`,`raw_text`,`summary`,`category`,`event_date`,`formatted_date_for_alarm`,`event_time`,`is_recurring`,`person`,`person_pinyin`,`importance_level`,`type`,`done`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MemoryRecord) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.createdAt)
        statement.bindText(3, entity.rawText)
        statement.bindText(4, entity.summary)
        statement.bindText(5, entity.category)
        val _tmpEventDate: String? = entity.eventDate
        if (_tmpEventDate == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpEventDate)
        }
        val _tmpFormattedDateForAlarm: String? = entity.formattedDateForAlarm
        if (_tmpFormattedDateForAlarm == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpFormattedDateForAlarm)
        }
        val _tmpEventTime: String? = entity.eventTime
        if (_tmpEventTime == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpEventTime)
        }
        val _tmp: Int = if (entity.isRecurring) 1 else 0
        statement.bindLong(9, _tmp.toLong())
        val _tmpPerson: String? = entity.person
        if (_tmpPerson == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpPerson)
        }
        val _tmpPersonPinyin: String? = entity.personPinyin
        if (_tmpPersonPinyin == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpPersonPinyin)
        }
        statement.bindText(12, entity.importanceLevel)
        statement.bindText(13, entity.type)
        val _tmp_1: Int = if (entity.done) 1 else 0
        statement.bindLong(14, _tmp_1.toLong())
      }
    }
    this.__deleteAdapterOfMemoryRecord = object : EntityDeleteOrUpdateAdapter<MemoryRecord>() {
      protected override fun createQuery(): String = "DELETE FROM `memories` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: MemoryRecord) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfMemoryRecord = object : EntityDeleteOrUpdateAdapter<MemoryRecord>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `memories` SET `id` = ?,`created_at` = ?,`raw_text` = ?,`summary` = ?,`category` = ?,`event_date` = ?,`formatted_date_for_alarm` = ?,`event_time` = ?,`is_recurring` = ?,`person` = ?,`person_pinyin` = ?,`importance_level` = ?,`type` = ?,`done` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: MemoryRecord) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.createdAt)
        statement.bindText(3, entity.rawText)
        statement.bindText(4, entity.summary)
        statement.bindText(5, entity.category)
        val _tmpEventDate: String? = entity.eventDate
        if (_tmpEventDate == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpEventDate)
        }
        val _tmpFormattedDateForAlarm: String? = entity.formattedDateForAlarm
        if (_tmpFormattedDateForAlarm == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpFormattedDateForAlarm)
        }
        val _tmpEventTime: String? = entity.eventTime
        if (_tmpEventTime == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpEventTime)
        }
        val _tmp: Int = if (entity.isRecurring) 1 else 0
        statement.bindLong(9, _tmp.toLong())
        val _tmpPerson: String? = entity.person
        if (_tmpPerson == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpPerson)
        }
        val _tmpPersonPinyin: String? = entity.personPinyin
        if (_tmpPersonPinyin == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpPersonPinyin)
        }
        statement.bindText(12, entity.importanceLevel)
        statement.bindText(13, entity.type)
        val _tmp_1: Int = if (entity.done) 1 else 0
        statement.bindLong(14, _tmp_1.toLong())
        statement.bindLong(15, entity.id)
      }
    }
  }

  public override suspend fun insert(record: MemoryRecord): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __insertAdapterOfMemoryRecord.insertAndReturnId(_connection, record)
    _result
  }

  public override suspend fun delete(record: MemoryRecord): Unit = performSuspending(__db, false,
      true) { _connection ->
    __deleteAdapterOfMemoryRecord.handle(_connection, record)
  }

  public override suspend fun update(record: MemoryRecord): Unit = performSuspending(__db, false,
      true) { _connection ->
    __updateAdapterOfMemoryRecord.handle(_connection, record)
  }

  public override fun getAllFlow(): Flow<List<MemoryRecord>> {
    val _sql: String = "SELECT * FROM memories ORDER BY created_at DESC"
    return createFlow(__db, false, arrayOf("memories")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfRawText: Int = getColumnIndexOrThrow(_stmt, "raw_text")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfEventDate: Int = getColumnIndexOrThrow(_stmt, "event_date")
        val _columnIndexOfFormattedDateForAlarm: Int = getColumnIndexOrThrow(_stmt,
            "formatted_date_for_alarm")
        val _columnIndexOfEventTime: Int = getColumnIndexOrThrow(_stmt, "event_time")
        val _columnIndexOfIsRecurring: Int = getColumnIndexOrThrow(_stmt, "is_recurring")
        val _columnIndexOfPerson: Int = getColumnIndexOrThrow(_stmt, "person")
        val _columnIndexOfPersonPinyin: Int = getColumnIndexOrThrow(_stmt, "person_pinyin")
        val _columnIndexOfImportanceLevel: Int = getColumnIndexOrThrow(_stmt, "importance_level")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDone: Int = getColumnIndexOrThrow(_stmt, "done")
        val _result: MutableList<MemoryRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: MemoryRecord
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpRawText: String
          _tmpRawText = _stmt.getText(_columnIndexOfRawText)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpEventDate: String?
          if (_stmt.isNull(_columnIndexOfEventDate)) {
            _tmpEventDate = null
          } else {
            _tmpEventDate = _stmt.getText(_columnIndexOfEventDate)
          }
          val _tmpFormattedDateForAlarm: String?
          if (_stmt.isNull(_columnIndexOfFormattedDateForAlarm)) {
            _tmpFormattedDateForAlarm = null
          } else {
            _tmpFormattedDateForAlarm = _stmt.getText(_columnIndexOfFormattedDateForAlarm)
          }
          val _tmpEventTime: String?
          if (_stmt.isNull(_columnIndexOfEventTime)) {
            _tmpEventTime = null
          } else {
            _tmpEventTime = _stmt.getText(_columnIndexOfEventTime)
          }
          val _tmpIsRecurring: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRecurring).toInt()
          _tmpIsRecurring = _tmp != 0
          val _tmpPerson: String?
          if (_stmt.isNull(_columnIndexOfPerson)) {
            _tmpPerson = null
          } else {
            _tmpPerson = _stmt.getText(_columnIndexOfPerson)
          }
          val _tmpPersonPinyin: String?
          if (_stmt.isNull(_columnIndexOfPersonPinyin)) {
            _tmpPersonPinyin = null
          } else {
            _tmpPersonPinyin = _stmt.getText(_columnIndexOfPersonPinyin)
          }
          val _tmpImportanceLevel: String
          _tmpImportanceLevel = _stmt.getText(_columnIndexOfImportanceLevel)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDone: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfDone).toInt()
          _tmpDone = _tmp_1 != 0
          _item =
              MemoryRecord(_tmpId,_tmpCreatedAt,_tmpRawText,_tmpSummary,_tmpCategory,_tmpEventDate,_tmpFormattedDateForAlarm,_tmpEventTime,_tmpIsRecurring,_tmpPerson,_tmpPersonPinyin,_tmpImportanceLevel,_tmpType,_tmpDone)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAll(): List<MemoryRecord> {
    val _sql: String = "SELECT * FROM memories ORDER BY created_at DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfRawText: Int = getColumnIndexOrThrow(_stmt, "raw_text")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfEventDate: Int = getColumnIndexOrThrow(_stmt, "event_date")
        val _columnIndexOfFormattedDateForAlarm: Int = getColumnIndexOrThrow(_stmt,
            "formatted_date_for_alarm")
        val _columnIndexOfEventTime: Int = getColumnIndexOrThrow(_stmt, "event_time")
        val _columnIndexOfIsRecurring: Int = getColumnIndexOrThrow(_stmt, "is_recurring")
        val _columnIndexOfPerson: Int = getColumnIndexOrThrow(_stmt, "person")
        val _columnIndexOfPersonPinyin: Int = getColumnIndexOrThrow(_stmt, "person_pinyin")
        val _columnIndexOfImportanceLevel: Int = getColumnIndexOrThrow(_stmt, "importance_level")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDone: Int = getColumnIndexOrThrow(_stmt, "done")
        val _result: MutableList<MemoryRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: MemoryRecord
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpRawText: String
          _tmpRawText = _stmt.getText(_columnIndexOfRawText)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpEventDate: String?
          if (_stmt.isNull(_columnIndexOfEventDate)) {
            _tmpEventDate = null
          } else {
            _tmpEventDate = _stmt.getText(_columnIndexOfEventDate)
          }
          val _tmpFormattedDateForAlarm: String?
          if (_stmt.isNull(_columnIndexOfFormattedDateForAlarm)) {
            _tmpFormattedDateForAlarm = null
          } else {
            _tmpFormattedDateForAlarm = _stmt.getText(_columnIndexOfFormattedDateForAlarm)
          }
          val _tmpEventTime: String?
          if (_stmt.isNull(_columnIndexOfEventTime)) {
            _tmpEventTime = null
          } else {
            _tmpEventTime = _stmt.getText(_columnIndexOfEventTime)
          }
          val _tmpIsRecurring: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRecurring).toInt()
          _tmpIsRecurring = _tmp != 0
          val _tmpPerson: String?
          if (_stmt.isNull(_columnIndexOfPerson)) {
            _tmpPerson = null
          } else {
            _tmpPerson = _stmt.getText(_columnIndexOfPerson)
          }
          val _tmpPersonPinyin: String?
          if (_stmt.isNull(_columnIndexOfPersonPinyin)) {
            _tmpPersonPinyin = null
          } else {
            _tmpPersonPinyin = _stmt.getText(_columnIndexOfPersonPinyin)
          }
          val _tmpImportanceLevel: String
          _tmpImportanceLevel = _stmt.getText(_columnIndexOfImportanceLevel)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDone: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfDone).toInt()
          _tmpDone = _tmp_1 != 0
          _item =
              MemoryRecord(_tmpId,_tmpCreatedAt,_tmpRawText,_tmpSummary,_tmpCategory,_tmpEventDate,_tmpFormattedDateForAlarm,_tmpEventTime,_tmpIsRecurring,_tmpPerson,_tmpPersonPinyin,_tmpImportanceLevel,_tmpType,_tmpDone)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): MemoryRecord? {
    val _sql: String = "SELECT * FROM memories WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfRawText: Int = getColumnIndexOrThrow(_stmt, "raw_text")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfEventDate: Int = getColumnIndexOrThrow(_stmt, "event_date")
        val _columnIndexOfFormattedDateForAlarm: Int = getColumnIndexOrThrow(_stmt,
            "formatted_date_for_alarm")
        val _columnIndexOfEventTime: Int = getColumnIndexOrThrow(_stmt, "event_time")
        val _columnIndexOfIsRecurring: Int = getColumnIndexOrThrow(_stmt, "is_recurring")
        val _columnIndexOfPerson: Int = getColumnIndexOrThrow(_stmt, "person")
        val _columnIndexOfPersonPinyin: Int = getColumnIndexOrThrow(_stmt, "person_pinyin")
        val _columnIndexOfImportanceLevel: Int = getColumnIndexOrThrow(_stmt, "importance_level")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDone: Int = getColumnIndexOrThrow(_stmt, "done")
        val _result: MemoryRecord?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpRawText: String
          _tmpRawText = _stmt.getText(_columnIndexOfRawText)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpEventDate: String?
          if (_stmt.isNull(_columnIndexOfEventDate)) {
            _tmpEventDate = null
          } else {
            _tmpEventDate = _stmt.getText(_columnIndexOfEventDate)
          }
          val _tmpFormattedDateForAlarm: String?
          if (_stmt.isNull(_columnIndexOfFormattedDateForAlarm)) {
            _tmpFormattedDateForAlarm = null
          } else {
            _tmpFormattedDateForAlarm = _stmt.getText(_columnIndexOfFormattedDateForAlarm)
          }
          val _tmpEventTime: String?
          if (_stmt.isNull(_columnIndexOfEventTime)) {
            _tmpEventTime = null
          } else {
            _tmpEventTime = _stmt.getText(_columnIndexOfEventTime)
          }
          val _tmpIsRecurring: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRecurring).toInt()
          _tmpIsRecurring = _tmp != 0
          val _tmpPerson: String?
          if (_stmt.isNull(_columnIndexOfPerson)) {
            _tmpPerson = null
          } else {
            _tmpPerson = _stmt.getText(_columnIndexOfPerson)
          }
          val _tmpPersonPinyin: String?
          if (_stmt.isNull(_columnIndexOfPersonPinyin)) {
            _tmpPersonPinyin = null
          } else {
            _tmpPersonPinyin = _stmt.getText(_columnIndexOfPersonPinyin)
          }
          val _tmpImportanceLevel: String
          _tmpImportanceLevel = _stmt.getText(_columnIndexOfImportanceLevel)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDone: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfDone).toInt()
          _tmpDone = _tmp_1 != 0
          _result =
              MemoryRecord(_tmpId,_tmpCreatedAt,_tmpRawText,_tmpSummary,_tmpCategory,_tmpEventDate,_tmpFormattedDateForAlarm,_tmpEventTime,_tmpIsRecurring,_tmpPerson,_tmpPersonPinyin,_tmpImportanceLevel,_tmpType,_tmpDone)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByCategory(category: String, limit: Int): List<MemoryRecord> {
    val _sql: String = """
        |
        |        SELECT * FROM memories
        |        WHERE category = ?
        |        ORDER BY created_at DESC
        |        LIMIT ?
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, category)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfRawText: Int = getColumnIndexOrThrow(_stmt, "raw_text")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfEventDate: Int = getColumnIndexOrThrow(_stmt, "event_date")
        val _columnIndexOfFormattedDateForAlarm: Int = getColumnIndexOrThrow(_stmt,
            "formatted_date_for_alarm")
        val _columnIndexOfEventTime: Int = getColumnIndexOrThrow(_stmt, "event_time")
        val _columnIndexOfIsRecurring: Int = getColumnIndexOrThrow(_stmt, "is_recurring")
        val _columnIndexOfPerson: Int = getColumnIndexOrThrow(_stmt, "person")
        val _columnIndexOfPersonPinyin: Int = getColumnIndexOrThrow(_stmt, "person_pinyin")
        val _columnIndexOfImportanceLevel: Int = getColumnIndexOrThrow(_stmt, "importance_level")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDone: Int = getColumnIndexOrThrow(_stmt, "done")
        val _result: MutableList<MemoryRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: MemoryRecord
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpRawText: String
          _tmpRawText = _stmt.getText(_columnIndexOfRawText)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpEventDate: String?
          if (_stmt.isNull(_columnIndexOfEventDate)) {
            _tmpEventDate = null
          } else {
            _tmpEventDate = _stmt.getText(_columnIndexOfEventDate)
          }
          val _tmpFormattedDateForAlarm: String?
          if (_stmt.isNull(_columnIndexOfFormattedDateForAlarm)) {
            _tmpFormattedDateForAlarm = null
          } else {
            _tmpFormattedDateForAlarm = _stmt.getText(_columnIndexOfFormattedDateForAlarm)
          }
          val _tmpEventTime: String?
          if (_stmt.isNull(_columnIndexOfEventTime)) {
            _tmpEventTime = null
          } else {
            _tmpEventTime = _stmt.getText(_columnIndexOfEventTime)
          }
          val _tmpIsRecurring: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRecurring).toInt()
          _tmpIsRecurring = _tmp != 0
          val _tmpPerson: String?
          if (_stmt.isNull(_columnIndexOfPerson)) {
            _tmpPerson = null
          } else {
            _tmpPerson = _stmt.getText(_columnIndexOfPerson)
          }
          val _tmpPersonPinyin: String?
          if (_stmt.isNull(_columnIndexOfPersonPinyin)) {
            _tmpPersonPinyin = null
          } else {
            _tmpPersonPinyin = _stmt.getText(_columnIndexOfPersonPinyin)
          }
          val _tmpImportanceLevel: String
          _tmpImportanceLevel = _stmt.getText(_columnIndexOfImportanceLevel)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDone: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfDone).toInt()
          _tmpDone = _tmp_1 != 0
          _item =
              MemoryRecord(_tmpId,_tmpCreatedAt,_tmpRawText,_tmpSummary,_tmpCategory,_tmpEventDate,_tmpFormattedDateForAlarm,_tmpEventTime,_tmpIsRecurring,_tmpPerson,_tmpPersonPinyin,_tmpImportanceLevel,_tmpType,_tmpDone)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchByKeyword(keyword: String, limit: Int): List<MemoryRecord> {
    val _sql: String = """
        |
        |        SELECT * FROM memories
        |        WHERE summary LIKE '%' || ? || '%'
        |           OR raw_text LIKE '%' || ? || '%'
        |           OR person LIKE '%' || ? || '%'
        |           OR person_pinyin LIKE '%' || ? || '%'
        |        ORDER BY created_at DESC
        |        LIMIT ?
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, keyword)
        _argIndex = 2
        _stmt.bindText(_argIndex, keyword)
        _argIndex = 3
        _stmt.bindText(_argIndex, keyword)
        _argIndex = 4
        _stmt.bindText(_argIndex, keyword)
        _argIndex = 5
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfRawText: Int = getColumnIndexOrThrow(_stmt, "raw_text")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfEventDate: Int = getColumnIndexOrThrow(_stmt, "event_date")
        val _columnIndexOfFormattedDateForAlarm: Int = getColumnIndexOrThrow(_stmt,
            "formatted_date_for_alarm")
        val _columnIndexOfEventTime: Int = getColumnIndexOrThrow(_stmt, "event_time")
        val _columnIndexOfIsRecurring: Int = getColumnIndexOrThrow(_stmt, "is_recurring")
        val _columnIndexOfPerson: Int = getColumnIndexOrThrow(_stmt, "person")
        val _columnIndexOfPersonPinyin: Int = getColumnIndexOrThrow(_stmt, "person_pinyin")
        val _columnIndexOfImportanceLevel: Int = getColumnIndexOrThrow(_stmt, "importance_level")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDone: Int = getColumnIndexOrThrow(_stmt, "done")
        val _result: MutableList<MemoryRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: MemoryRecord
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpRawText: String
          _tmpRawText = _stmt.getText(_columnIndexOfRawText)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpEventDate: String?
          if (_stmt.isNull(_columnIndexOfEventDate)) {
            _tmpEventDate = null
          } else {
            _tmpEventDate = _stmt.getText(_columnIndexOfEventDate)
          }
          val _tmpFormattedDateForAlarm: String?
          if (_stmt.isNull(_columnIndexOfFormattedDateForAlarm)) {
            _tmpFormattedDateForAlarm = null
          } else {
            _tmpFormattedDateForAlarm = _stmt.getText(_columnIndexOfFormattedDateForAlarm)
          }
          val _tmpEventTime: String?
          if (_stmt.isNull(_columnIndexOfEventTime)) {
            _tmpEventTime = null
          } else {
            _tmpEventTime = _stmt.getText(_columnIndexOfEventTime)
          }
          val _tmpIsRecurring: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRecurring).toInt()
          _tmpIsRecurring = _tmp != 0
          val _tmpPerson: String?
          if (_stmt.isNull(_columnIndexOfPerson)) {
            _tmpPerson = null
          } else {
            _tmpPerson = _stmt.getText(_columnIndexOfPerson)
          }
          val _tmpPersonPinyin: String?
          if (_stmt.isNull(_columnIndexOfPersonPinyin)) {
            _tmpPersonPinyin = null
          } else {
            _tmpPersonPinyin = _stmt.getText(_columnIndexOfPersonPinyin)
          }
          val _tmpImportanceLevel: String
          _tmpImportanceLevel = _stmt.getText(_columnIndexOfImportanceLevel)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDone: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfDone).toInt()
          _tmpDone = _tmp_1 != 0
          _item =
              MemoryRecord(_tmpId,_tmpCreatedAt,_tmpRawText,_tmpSummary,_tmpCategory,_tmpEventDate,_tmpFormattedDateForAlarm,_tmpEventTime,_tmpIsRecurring,_tmpPerson,_tmpPersonPinyin,_tmpImportanceLevel,_tmpType,_tmpDone)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getWithEventDate(limit: Int): List<MemoryRecord> {
    val _sql: String = """
        |
        |        SELECT * FROM memories
        |        WHERE event_date IS NOT NULL
        |          AND event_date != ''
        |        ORDER BY event_date DESC, created_at DESC
        |        LIMIT ?
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfRawText: Int = getColumnIndexOrThrow(_stmt, "raw_text")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfEventDate: Int = getColumnIndexOrThrow(_stmt, "event_date")
        val _columnIndexOfFormattedDateForAlarm: Int = getColumnIndexOrThrow(_stmt,
            "formatted_date_for_alarm")
        val _columnIndexOfEventTime: Int = getColumnIndexOrThrow(_stmt, "event_time")
        val _columnIndexOfIsRecurring: Int = getColumnIndexOrThrow(_stmt, "is_recurring")
        val _columnIndexOfPerson: Int = getColumnIndexOrThrow(_stmt, "person")
        val _columnIndexOfPersonPinyin: Int = getColumnIndexOrThrow(_stmt, "person_pinyin")
        val _columnIndexOfImportanceLevel: Int = getColumnIndexOrThrow(_stmt, "importance_level")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDone: Int = getColumnIndexOrThrow(_stmt, "done")
        val _result: MutableList<MemoryRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: MemoryRecord
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpRawText: String
          _tmpRawText = _stmt.getText(_columnIndexOfRawText)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpEventDate: String?
          if (_stmt.isNull(_columnIndexOfEventDate)) {
            _tmpEventDate = null
          } else {
            _tmpEventDate = _stmt.getText(_columnIndexOfEventDate)
          }
          val _tmpFormattedDateForAlarm: String?
          if (_stmt.isNull(_columnIndexOfFormattedDateForAlarm)) {
            _tmpFormattedDateForAlarm = null
          } else {
            _tmpFormattedDateForAlarm = _stmt.getText(_columnIndexOfFormattedDateForAlarm)
          }
          val _tmpEventTime: String?
          if (_stmt.isNull(_columnIndexOfEventTime)) {
            _tmpEventTime = null
          } else {
            _tmpEventTime = _stmt.getText(_columnIndexOfEventTime)
          }
          val _tmpIsRecurring: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRecurring).toInt()
          _tmpIsRecurring = _tmp != 0
          val _tmpPerson: String?
          if (_stmt.isNull(_columnIndexOfPerson)) {
            _tmpPerson = null
          } else {
            _tmpPerson = _stmt.getText(_columnIndexOfPerson)
          }
          val _tmpPersonPinyin: String?
          if (_stmt.isNull(_columnIndexOfPersonPinyin)) {
            _tmpPersonPinyin = null
          } else {
            _tmpPersonPinyin = _stmt.getText(_columnIndexOfPersonPinyin)
          }
          val _tmpImportanceLevel: String
          _tmpImportanceLevel = _stmt.getText(_columnIndexOfImportanceLevel)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDone: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfDone).toInt()
          _tmpDone = _tmp_1 != 0
          _item =
              MemoryRecord(_tmpId,_tmpCreatedAt,_tmpRawText,_tmpSummary,_tmpCategory,_tmpEventDate,_tmpFormattedDateForAlarm,_tmpEventTime,_tmpIsRecurring,_tmpPerson,_tmpPersonPinyin,_tmpImportanceLevel,_tmpType,_tmpDone)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getLatestByCategory(category: String): MemoryRecord? {
    val _sql: String = """
        |
        |        SELECT * FROM memories
        |        WHERE category = ?
        |        ORDER BY created_at DESC
        |        LIMIT 1
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, category)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfRawText: Int = getColumnIndexOrThrow(_stmt, "raw_text")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfEventDate: Int = getColumnIndexOrThrow(_stmt, "event_date")
        val _columnIndexOfFormattedDateForAlarm: Int = getColumnIndexOrThrow(_stmt,
            "formatted_date_for_alarm")
        val _columnIndexOfEventTime: Int = getColumnIndexOrThrow(_stmt, "event_time")
        val _columnIndexOfIsRecurring: Int = getColumnIndexOrThrow(_stmt, "is_recurring")
        val _columnIndexOfPerson: Int = getColumnIndexOrThrow(_stmt, "person")
        val _columnIndexOfPersonPinyin: Int = getColumnIndexOrThrow(_stmt, "person_pinyin")
        val _columnIndexOfImportanceLevel: Int = getColumnIndexOrThrow(_stmt, "importance_level")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDone: Int = getColumnIndexOrThrow(_stmt, "done")
        val _result: MemoryRecord?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpRawText: String
          _tmpRawText = _stmt.getText(_columnIndexOfRawText)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpEventDate: String?
          if (_stmt.isNull(_columnIndexOfEventDate)) {
            _tmpEventDate = null
          } else {
            _tmpEventDate = _stmt.getText(_columnIndexOfEventDate)
          }
          val _tmpFormattedDateForAlarm: String?
          if (_stmt.isNull(_columnIndexOfFormattedDateForAlarm)) {
            _tmpFormattedDateForAlarm = null
          } else {
            _tmpFormattedDateForAlarm = _stmt.getText(_columnIndexOfFormattedDateForAlarm)
          }
          val _tmpEventTime: String?
          if (_stmt.isNull(_columnIndexOfEventTime)) {
            _tmpEventTime = null
          } else {
            _tmpEventTime = _stmt.getText(_columnIndexOfEventTime)
          }
          val _tmpIsRecurring: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRecurring).toInt()
          _tmpIsRecurring = _tmp != 0
          val _tmpPerson: String?
          if (_stmt.isNull(_columnIndexOfPerson)) {
            _tmpPerson = null
          } else {
            _tmpPerson = _stmt.getText(_columnIndexOfPerson)
          }
          val _tmpPersonPinyin: String?
          if (_stmt.isNull(_columnIndexOfPersonPinyin)) {
            _tmpPersonPinyin = null
          } else {
            _tmpPersonPinyin = _stmt.getText(_columnIndexOfPersonPinyin)
          }
          val _tmpImportanceLevel: String
          _tmpImportanceLevel = _stmt.getText(_columnIndexOfImportanceLevel)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDone: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfDone).toInt()
          _tmpDone = _tmp_1 != 0
          _result =
              MemoryRecord(_tmpId,_tmpCreatedAt,_tmpRawText,_tmpSummary,_tmpCategory,_tmpEventDate,_tmpFormattedDateForAlarm,_tmpEventTime,_tmpIsRecurring,_tmpPerson,_tmpPersonPinyin,_tmpImportanceLevel,_tmpType,_tmpDone)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getLatest(): MemoryRecord? {
    val _sql: String = "SELECT * FROM memories ORDER BY created_at DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfRawText: Int = getColumnIndexOrThrow(_stmt, "raw_text")
        val _columnIndexOfSummary: Int = getColumnIndexOrThrow(_stmt, "summary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfEventDate: Int = getColumnIndexOrThrow(_stmt, "event_date")
        val _columnIndexOfFormattedDateForAlarm: Int = getColumnIndexOrThrow(_stmt,
            "formatted_date_for_alarm")
        val _columnIndexOfEventTime: Int = getColumnIndexOrThrow(_stmt, "event_time")
        val _columnIndexOfIsRecurring: Int = getColumnIndexOrThrow(_stmt, "is_recurring")
        val _columnIndexOfPerson: Int = getColumnIndexOrThrow(_stmt, "person")
        val _columnIndexOfPersonPinyin: Int = getColumnIndexOrThrow(_stmt, "person_pinyin")
        val _columnIndexOfImportanceLevel: Int = getColumnIndexOrThrow(_stmt, "importance_level")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDone: Int = getColumnIndexOrThrow(_stmt, "done")
        val _result: MemoryRecord?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpRawText: String
          _tmpRawText = _stmt.getText(_columnIndexOfRawText)
          val _tmpSummary: String
          _tmpSummary = _stmt.getText(_columnIndexOfSummary)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpEventDate: String?
          if (_stmt.isNull(_columnIndexOfEventDate)) {
            _tmpEventDate = null
          } else {
            _tmpEventDate = _stmt.getText(_columnIndexOfEventDate)
          }
          val _tmpFormattedDateForAlarm: String?
          if (_stmt.isNull(_columnIndexOfFormattedDateForAlarm)) {
            _tmpFormattedDateForAlarm = null
          } else {
            _tmpFormattedDateForAlarm = _stmt.getText(_columnIndexOfFormattedDateForAlarm)
          }
          val _tmpEventTime: String?
          if (_stmt.isNull(_columnIndexOfEventTime)) {
            _tmpEventTime = null
          } else {
            _tmpEventTime = _stmt.getText(_columnIndexOfEventTime)
          }
          val _tmpIsRecurring: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsRecurring).toInt()
          _tmpIsRecurring = _tmp != 0
          val _tmpPerson: String?
          if (_stmt.isNull(_columnIndexOfPerson)) {
            _tmpPerson = null
          } else {
            _tmpPerson = _stmt.getText(_columnIndexOfPerson)
          }
          val _tmpPersonPinyin: String?
          if (_stmt.isNull(_columnIndexOfPersonPinyin)) {
            _tmpPersonPinyin = null
          } else {
            _tmpPersonPinyin = _stmt.getText(_columnIndexOfPersonPinyin)
          }
          val _tmpImportanceLevel: String
          _tmpImportanceLevel = _stmt.getText(_columnIndexOfImportanceLevel)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDone: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfDone).toInt()
          _tmpDone = _tmp_1 != 0
          _result =
              MemoryRecord(_tmpId,_tmpCreatedAt,_tmpRawText,_tmpSummary,_tmpCategory,_tmpEventDate,_tmpFormattedDateForAlarm,_tmpEventTime,_tmpIsRecurring,_tmpPerson,_tmpPersonPinyin,_tmpImportanceLevel,_tmpType,_tmpDone)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM memories"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun resetDailyTodoDoneFlags(): Int {
    val _sql: String = """
        |
        |        UPDATE memories SET done = 0
        |        WHERE type = 'todo'
        |          AND is_recurring = 1
        |          AND done = 1
        |          AND event_time IS NOT NULL
        |          AND event_time != ''
        |          AND category != 'birthday'
        |        
        """.trimMargin()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
