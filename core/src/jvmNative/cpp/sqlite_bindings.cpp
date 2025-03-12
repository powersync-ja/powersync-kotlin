#include <jni.h>
#include <sqlite3.h>
#include <string>
#include <cstring>
#include <map>
#include <android/log.h>

static JavaVM *g_javaVM = nullptr;

typedef struct context {
    jobject bindingsObj;
    jclass bindingsClz;
} Context;

std::map<sqlite3 *, Context> contextMap;  // Map to store contexts for each SQLite instance

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_javaVM = vm;
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;  // JNI version not supported.
    }
    return JNI_VERSION_1_6;
}

void releaseContext(sqlite3 *db) {
    if (contextMap.find(db) != contextMap.end()) {
        Context &ctx = contextMap[db];
        JNIEnv *env;
        if (g_javaVM->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK) {
            if (ctx.bindingsClz) {
                env->DeleteGlobalRef(ctx.bindingsClz);
            }
            if (ctx.bindingsObj) {
                env->DeleteGlobalRef(ctx.bindingsObj);
            }
        }
        contextMap.erase(db);
    }
}

static void
update_hook_callback(void *pData, int opCode, const char *pDbName, const char *pTableName,
                     sqlite3_int64 iRow) {
    JNIEnv *env;

    sqlite3 *db = (sqlite3 *) pData;
    if (contextMap.find(db) != contextMap.end()) {
        Context &ctx = contextMap[db];
        if (ctx.bindingsClz) {
            jmethodID updateId = env->GetMethodID(ctx.bindingsClz, "onTableUpdate",
                                                  "(Ljava/lang/String;)V");
            jstring tableString = env->NewStringUTF(pTableName);
            env->CallVoidMethod(ctx.bindingsObj, updateId, tableString);
            env->DeleteLocalRef(tableString);
        }
    }
}

static int commit_hook(void *pData) {
    JNIEnv *env;

    sqlite3 *db = (sqlite3 *) pData;
    if (contextMap.find(db) != contextMap.end()) {
        Context &ctx = contextMap[db];
        if (ctx.bindingsClz) {
            jmethodID methodId = env->GetMethodID(ctx.bindingsClz, "onTransactionCommit", "(Z)V");
            env->CallVoidMethod(ctx.bindingsObj, methodId, JNI_TRUE);
        }
    }

    return 0;
}

static void rollback_hook(void *pData) {
    JNIEnv *env;
    sqlite3 *db = (sqlite3 *) pData;
    if (contextMap.find(db) != contextMap.end()) {
        Context &ctx = contextMap[db];
        if (ctx.bindingsClz) {
            jmethodID methodId = env->GetMethodID(ctx.bindingsClz, "onTransactionRollback", "()V");
            env->CallVoidMethod(ctx.bindingsObj, methodId);
        }
    }

}

static void pointerFunc(sqlite3_context *context, int argc, sqlite3_value **argv) {
    sqlite3 *db = sqlite3_context_db_handle(context);
    sqlite3_result_int64(context,
                         (sqlite3_int64) (intptr_t) db);  // Use intptr_t for correct casting
    __android_log_print(ANDROID_LOG_INFO, "test", "xxx 0x%lx",
                        (long) (intptr_t) db);  // Correct format for long
}

JNIEXPORT int powersync_init(sqlite3 *db, char **pzErrMsg, const sqlite3_api_routines *pApi) {
    sqlite3_initialize();
    sqlite3_create_function(db, "get_db_pointer", 0, SQLITE_UTF8, NULL, pointerFunc, NULL, NULL);

    sqlite3_update_hook(db, update_hook_callback, db);
    sqlite3_commit_hook(db, commit_hook, db);
    sqlite3_rollback_hook(db, rollback_hook, db);

    return SQLITE_OK;
}

JNIEXPORT void JNICALL
Java_com_powersync_DatabaseDriverFactory_setupSqliteBinding(JNIEnv *env, jobject thiz,
                                                            jlong dbPointer) {
    if (dbPointer == 0) {
        __android_log_print(ANDROID_LOG_ERROR, "PowerSync",
                            "setupSqliteBinding: Invalid database pointer");
        return;
    }

    jclass clz = env->GetObjectClass(thiz);
    Context ctx;
    ctx.bindingsClz = (jclass) env->NewGlobalRef(clz);
    ctx.bindingsObj = env->NewGlobalRef(thiz);

    sqlite3 *db = reinterpret_cast<sqlite3 *>(dbPointer);
    contextMap[db] = ctx;
}

}
