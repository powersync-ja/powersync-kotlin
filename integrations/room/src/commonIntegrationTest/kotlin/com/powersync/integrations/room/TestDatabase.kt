package com.powersync.integrations.room

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
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

        override suspend fun onOpen(connection: SQLiteConnection) {
            connection.execSQL("CREATE VIRTUAL TABLE users_fts USING fts5(id UNINDEXED, name)")
        }
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("KotlinNoActualForExpect")
expect object TestDatabaseConstructor : RoomDatabaseConstructor<TestDatabase> {
    override fun initialize(): TestDatabase
}
