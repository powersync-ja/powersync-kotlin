#include <jni.h>
#include <inttypes.h>
#include <sqlite3.h>
#include <string>

typedef struct context {
    JavaVM *javaVM;
    jobject bindingsObj;
    jclass bindingsClz;
} Context;
Context g_ctx;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    memset(&g_ctx, 0, sizeof(g_ctx));
    g_ctx.javaVM = vm;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;  // JNI version not supported.
    }

    return JNI_VERSION_1_6;
}

static void
update_hook_callback(void *pData, int opCode, char const *pDbName, char const *pTableName,
                     sqlite3_int64 iRow) {
    // Get JNIEnv for the current thread
    JNIEnv *env;
    JavaVM *javaVM = g_ctx.javaVM;

    jint res = javaVM->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = javaVM->AttachCurrentThread(&env, NULL);
        if (JNI_OK != res) {
            return;
        }
    }

    if (g_ctx.bindingsClz) {
        jmethodID updateId = env->GetMethodID(
                g_ctx.bindingsClz, "onUpdate", "(ILjava/lang/String;Ljava/lang/String;J)V");

        jstring dbString = env->NewStringUTF(std::string(pDbName).c_str());
        jstring tableString = env->NewStringUTF(std::string(pTableName).c_str());

        env->CallVoidMethod(g_ctx.bindingsObj, updateId, opCode, dbString, tableString,
                            iRow);
    }
}

jint powersync_init(sqlite3 *db, char **pzErrMsg,
                    const sqlite3_api_routines *pApi) {

    // Set the update hook for the SQLite database
    sqlite3_update_hook(db, update_hook_callback, NULL);

    return SQLITE_OK;
}

JNIEXPORT void JNICALL
Java_com_powersync_DatabaseDriverFactory_setupSqliteUpdateHook(JNIEnv *env, jobject thiz) {
    jclass clz = env->GetObjectClass(thiz);
    g_ctx.bindingsClz = (jclass) env->NewGlobalRef(clz);
    g_ctx.bindingsObj = env->NewGlobalRef(thiz);
}
}