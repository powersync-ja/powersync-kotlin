package co.powersync.kotlin

import io.requery.android.database.sqlite.SQLiteDatabase

import kotlinx.coroutines.CompletableDeferred

class PowerSyncDatabase: AbstractPowerSyncDatabase() {
    lateinit var database: SQLiteDatabase

    override suspend fun getNextCrudTransaction() {
        TODO("Not yet implemented")
    }

    // TODO how do I send Void?
    var initCompleted: CompletableDeferred<String>? = null;

    suspend fun init(){
        initCompleted = CompletableDeferred<String>();

        // DO STUFF

        initCompleted?.complete("Done");
    }

    suspend fun connect(connector: PowerSyncBackendConnector){
        disconnect()

        // If isCompleted returns true, that means that init had completed (Failure also counts as completed)
        if(initCompleted?.isCompleted != true){
            initCompleted?.await();
        }
        database.

    }

    suspend fun disconnect(){
        TODO("Not yet implemented")
    }
}