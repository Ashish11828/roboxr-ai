package com.example.roboxr.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.roboxr.model.AuditRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "audit_records")
data class AuditRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: String,
    val operatorId: String,
    val taskName: String,
    val status: String,
    val sha256: String,
    val engine: String,
    val waypointCount: Int,
    val programCode: String
) {
    fun toDomain() = AuditRecord(
        id = id,
        timestamp = timestamp,
        operatorId = operatorId,
        taskName = taskName,
        status = status,
        sha256 = sha256,
        engine = engine,
        waypointCount = waypointCount,
        programCode = programCode
    )

    companion object {
        fun fromDomain(record: AuditRecord) = AuditRecordEntity(
            id = record.id,
            timestamp = record.timestamp,
            operatorId = record.operatorId,
            taskName = record.taskName,
            status = record.status,
            sha256 = record.sha256,
            engine = record.engine,
            waypointCount = record.waypointCount,
            programCode = record.programCode
        )
    }
}

@Dao
interface AuditRecordDao {
    @Query("SELECT * FROM audit_records ORDER BY id DESC")
    fun getAllRecords(): Flow<List<AuditRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AuditRecordEntity)

    @Query("DELETE FROM audit_records")
    suspend fun clearAll()
}

@Database(entities = [AuditRecordEntity::class], version = 1, exportSchema = false)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun auditRecordDao(): AuditRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AuditDatabase? = null

        fun getInstance(context: Context): AuditDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuditDatabase::class.java,
                    "roboxr_standalone_audit.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class AuditRepository(private val dao: AuditRecordDao) {
    val allRecords: Flow<List<AuditRecord>> = dao.getAllRecords().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun recordAudit(record: AuditRecord) {
        dao.insertRecord(AuditRecordEntity.fromDomain(record))
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
