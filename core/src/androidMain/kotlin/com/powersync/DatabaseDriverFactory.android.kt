package com.powersync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.powersync.db.internal.InternalSchema
import com.powersync.persistence.driver.AndroidSqliteDriver
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import kotlinx.coroutines.CoroutineScope
import okhttp3.internal.toHexString

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory(
    private val context: Context,
) {
    private var driver: PsSqlDriver? = null

    private external fun setupSqliteBinding(dbPointer: Long)

    @Suppress("unused")
    private fun onTableUpdate(tableName: String) {
        driver?.updateTable(tableName)
    }

    @Suppress("unused")
    private fun onTransactionCommit(success: Boolean) {
        driver?.also { driver ->
            // Only clear updates if a rollback happened
            // We manually fire updates when transactions are completed
            if (!success) {
                driver.clearTableUpdates()
            }
        }
    }

    internal actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
    ): PsSqlDriver {
        val schema = InternalSchema

        this.driver =
            PsSqlDriver(
                driver =
                    AndroidSqliteDriver(
                        context = context,
                        schema = schema,
                        name = dbFilename,
                        factory =
                            RequerySQLiteOpenHelperFactory(
                                listOf(
                                    RequerySQLiteOpenHelperFactory.ConfigurationOptions { config ->
                                        config.customExtensions.add(
                                            SQLiteCustomExtension(
                                                "libpowersync",
                                                "sqlite3_powersync_init",
                                            ),
                                        )
                                        config.customExtensions.add(
                                            SQLiteCustomExtension(
                                                "libpowersync-sqlite",
                                                "powersync_init",
                                            ),
                                        )
                                        config
                                    },
                                ),
                            ),
                        callback =
                            object : AndroidSqliteDriver.Callback(schema) {
                                override fun onOpen(db: SupportSQLiteDatabase) {
                                    super.onCreate(db)

                                    val cursor = db.query("SELECT get_db_pointer()")
                                    val pointer: Long
                                    cursor.use {
                                        if (cursor.moveToFirst()) { // Move to the first row
                                            pointer = cursor.getLong(0)
                                            println("xxx SQLite Pointer: ${pointer.toHexString()}")
                                        } else {
                                            throw IllegalStateException("No result from get_db_pointer()")
                                        }
                                    }
                                    setupSqliteBinding(pointer)
                                }

                                override fun onConfigure(db: SupportSQLiteDatabase) {
                                    db.enableWriteAheadLogging()
                                    super.onConfigure(db)
                                }
                            },
                    ),
            )
        return this.driver as PsSqlDriver
    }

    public companion object {
        init {
            System.loadLibrary("powersync-sqlite")
        }
    }
}
