package com.powersync.integrations.room

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Database(entities = [User::class], version = 1)
abstract class TestDatabase: RoomDatabase() {
    abstract fun userDao(): UserDao
}

@Dao
interface UserDao {
    @Query("INSERT INTO user (id, name) VALUES (uuid(), :name)")
    fun create(name: String)

    @Query("SELECT * FROM user")
    fun getAll(): List<User>

    @Delete
    fun delete(user: User)
}

@Entity
data class User(
    @PrimaryKey val id: String,
    val name: String,
)
