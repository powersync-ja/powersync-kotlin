package com.powersync.integrations.room

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.InvalidationTracker
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomOpenDelegateMarker
import com.powersync.db.schema.Column
import com.powersync.db.schema.Table
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Database(
    entities = [TodoEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase(): RoomDatabase() {
    abstract fun todosDao(): TodosDao
}

class AppDatabaseForPowerSync(
    createTodosDao: (AppDatabase) -> TodosDao
): AppDatabase() {
    private val _todosDao by lazy { createTodosDao(this) }

    override fun todosDao(): TodosDao = _todosDao

    override fun createOpenDelegate(): RoomOpenDelegateMarker {
        return PowerSyncOpenDelegate()
    }

    override fun createInvalidationTracker(): InvalidationTracker {
        TODO("Not yet implemented")
    }
}

@Dao
interface TodosDao {
    @Insert
    suspend fun addEntry(entry: TodoEntity)

    @Query("SELECT count(*) FROM todos")
    suspend fun count(): Int

    @Query("SELECT * FROM todos")
    fun all(): Flow<List<TodoEntity>>
}

@OptIn(ExperimentalUuidApi::class)
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey
    val id: String = Uuid.random().toHexDashString(),
    val title: String,
    val content: String,
) {
    companion object {
        val TABLE = Table(
            name = "todos",
            columns = listOf(
                Column.text("title"),
                Column.text("content"),
            )
        )
    }
}
