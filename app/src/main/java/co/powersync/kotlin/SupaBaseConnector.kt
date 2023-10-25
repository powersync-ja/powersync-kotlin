package co.powersync.kotlin
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class SupaBaseConnector: PowerSyncBackendConnector() {

    companion object {
        // TODO this needs to be provided by the user/dev
        private const val POWERSYNC_URL = "https://6528055654e498b08254c372.powersync.journeyapps.com"
        private const val SUPABASE_URL = "https://hlrnpckmfalpixgmpvxb.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhscm5wY2ttZmFscGl4Z21wdnhiIiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTcxMjA0NzgsImV4cCI6MjAxMjY5NjQ3OH0.kQH5mN7ggAWE_9RGlrd9jnVSUbK3kQle1PIyvMGmRvg"

        private const val TEST_EMAIL = "hello@example.com";
        private const val TEST_PASSWORD = "123456";
    }

    private val supabaseClient: SupabaseClient;

    private fun createClient(): SupabaseClient {
        val client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(GoTrue)
            install(Postgrest)
        }

        return client
    }

    private suspend fun login(): Unit {
        val res = supabaseClient.gotrue.loginWith(Email){
            email = TEST_EMAIL
            password = TEST_PASSWORD
        }

        return res;
    }

    override suspend fun fetchCredentials(): PowerSyncCredentials {
        val session = supabaseClient.gotrue.currentSessionOrNull();

        if (session == null) {
            throw Exception("Could not fetch Supabase credentials")
        }

        if (session.user == null) {
            throw Exception("No user data")
        }

        println("session expires at " + session.expiresAt);

        return PowerSyncCredentials(
            client = supabaseClient,
            endpoint = POWERSYNC_URL,
            token = session.accessToken,
            expiresAt = session.expiresAt,
            // !! required because error: Smart cast to 'UserInfo' is impossible, because 'session.user' is a public API property declared in different module
            // TODO figure out what error is about
            userID = session.user!!.id
        );
    }

    override suspend fun uploadData(database: AbstractPowerSyncDatabase) {
        val transaction = database.getNextCrudTransaction() ?: return

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


    init {
        supabaseClient = createClient()

        GlobalScope.launch {
            val loginRes = login();
            val creds = fetchCredentials();
            println("GOT Login res")
        }
//        login();
        println("GOT TO HERE")
    }
}