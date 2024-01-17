package co.powersync.connectors

import co.powersync.db.PowerSyncDatabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.runBlocking

class SupabaseConnector : PowerSyncBackendConnector() {

    companion object {
        // TODO this needs to be provided by the user/dev
        private const val POWERSYNC_URL =
            "https://65a0e6bb4078d9a211d3cffb.powersync.journeyapps.com"
        private const val SUPABASE_URL = "https://wtilkjczshmzekrjelco.supabase.co"
        private const val SUPABASE_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind0aWxramN6c2htemVrcmplbGNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MDUwNDM2MTYsImV4cCI6MjAyMDYxOTYxNn0.E4DWa1ftn92_rQP-aLTsQHsZufouhMmzBfsCiX2p5eM"

        private const val TEST_EMAIL = "hello@powersync.com";
        private const val TEST_PASSWORD = "@dYX0}72eS0kT=(YG@8(";
    }

    private val supabaseClient: SupabaseClient;

    init {
        supabaseClient = createClient()

        runBlocking {
            login()
            val creds = fetchCredentials()
            println("Creds $creds")
        }
    }

    private fun createClient(): SupabaseClient {
        val client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }

        return client
    }

    private suspend fun login(): Unit {
        val res = supabaseClient.auth.signInWith(Email) {
            email = TEST_EMAIL
            password = TEST_PASSWORD
        }

        return res;
    }

    override suspend fun fetchCredentials(): PowerSyncCredentials {
        val session = supabaseClient.auth.currentSessionOrNull()
            ?: throw Exception("Could not fetch Supabase credentials");

        if (session.user == null) {
            throw Exception("No user data")
        }

        return PowerSyncCredentials(
            endpoint = POWERSYNC_URL,
            token = session.accessToken,
            expiresAt = session.expiresAt,
            userId = session.user!!.id
        );
    }

    override suspend fun uploadData(database: PowerSyncDatabase) {
//        val transaction = database.getNextCrudTransaction() ?: return

    }

    // TODO implement uploadData
//    suspend fun uploadData(database: AbstractPowerSyncDatabase): Promise<void> {
//        const transaction = await database.getNextCrudTransaction();
//
//        if (!transaction) {
//            return;
//        }
//
//        let lastOp: CrudEntry | null = null;
//        try {
//            // Note: If transactional consistency is important, use database functions
//            // or edge functions to process the entire transaction in a single call.
//            for (let op of transaction.crud) {
//                lastOp = op;
//                const table = this.supabaseClient.from(op.table);
//                switch (op.op) {
//                    case UpdateType.PUT:
//                    const record = { ...op.opData, id: op.id };
//                    const { error } = await table.upsert(record);
//                    if (error) {
//                        throw new Error(`Could not upsert data to Supabase ${JSON.stringify(error)}`);
//                    }
//                    break;
//                    case UpdateType.PATCH:
//                    await table.update(op.opData).eq('id', op.id);
//                    break;
//                    case UpdateType.DELETE:
//                    await table.delete().eq('id', op.id);
//                    break;
//                }
//            }
//
//            await transaction.complete();
//        } catch (ex: any) {
//            console.debug(ex);
//            if (typeof ex.code == 'string' && FATAL_RESPONSE_CODES.some((regex) => regex.test(ex.code))) {
//                /**
//                 * Instead of blocking the queue with these errors,
//                 * discard the (rest of the) transaction.
//                 *
//                 * Note that these errors typically indicate a bug in the application.
//                 * If protecting against data loss is important, save the failing records
//                 * elsewhere instead of discarding, and/or notify the user.
//                 */
//                console.error(`Data upload error - discarding ${lastOp}`, ex);
//                await transaction.complete();
//            } else {
//                // Error may be retryable - e.g. network error or temporary server error.
//                // Throwing an error here causes this call to be retried after a delay.
//                throw ex;
//            }
//        }
//    }


}