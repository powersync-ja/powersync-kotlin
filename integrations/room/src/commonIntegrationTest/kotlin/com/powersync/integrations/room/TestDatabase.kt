package com.powersync.integrations.room

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.powersync.db.schema.RawTable
import com.powersync.db.schema.RawTableSchema
import com.powersync.db.schema.Schema
import kotlinx.coroutines.flow.Flow

@Entity
data class User(
    @PrimaryKey val id: String,
    val name: String,
)

@Dao
interface UserDao {
    @Insert
    suspend fun create(user: User)

    @Query("SELECT * FROM user")
    suspend fun getAll(): List<User>

    @Query("SELECT * FROM user")
    fun watchAll(): Flow<List<User>>

    @Delete
    suspend fun delete(user: User)
}

@Database(entities = [User::class], version = 1, exportSchema = false)
@ConstructedBy(TestDatabaseConstructor::class)
abstract class TestDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object : Callback() {
        val schema =
            Schema(
                RawTable(
                    name = "user",
                    schema = RawTableSchema("user"),
                ),
            )

        override fun onOpen(connection: SQLiteConnection) {
            connection.execSQL("CREATE VIRTUAL TABLE users_fts USING fts5(id UNINDEXED, name)")
        }
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("KotlinNoActualForExpect")
expect object TestDatabaseConstructor : RoomDatabaseConstructor<TestDatabase> {
    override fun initialize(): TestDatabase
}
