#include <jni.h>
#include <sqlite3.h>
#include <string>
#include <cstring>

typedef struct context {
    JavaVM *javaVM;
    jobject bindingsObj;
    jclass bindingsClz;
} Context;
Context g_ctx;

extern "C" {

JNIEXPORT
jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    memset(&g_ctx, 0, sizeof(g_ctx));
    g_ctx.javaVM = vm;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;  // JNI version not supported.
    }

    return JNI_VERSION_1_6;
}

static void update_hook_callback(void *pData, int opCode, char const *pDbName, char const *pTableName, sqlite3_int64 iRow) {
    // Get JNIEnv for the current thread
    JNIEnv *env;
    JavaVM *javaVM = g_ctx.javaVM;
    javaVM->GetEnv((void **) &env, JNI_VERSION_1_6);

    if (g_ctx.bindingsClz) {
        jmethodID updateId = env->GetMethodID(
                g_ctx.bindingsClz, "onTableUpdate", "(Ljava/lang/String;)V");

        jstring tableString = env->NewStringUTF(std::string(pTableName).c_str());
        env->CallVoidMethod(g_ctx.bindingsObj, updateId, tableString);
    }
}

static int commit_hook(void *pool) {
    // Get JNIEnv for the current thread
    JNIEnv *env;
    JavaVM *javaVM = g_ctx.javaVM;
    javaVM->GetEnv((void **) &env, JNI_VERSION_1_6);

    if (g_ctx.bindingsClz) {
        jmethodID methodId = env->GetMethodID(
                g_ctx.bindingsClz, "onTransactionCommit", "(Z)V");

        env->CallVoidMethod(g_ctx.bindingsObj, methodId, JNI_TRUE);
    }

    return 0;
}

static void rollback_hook(void *pool) {
    // Get JNIEnv for the current thread
    JNIEnv *env;
    JavaVM *javaVM = g_ctx.javaVM;
    javaVM->GetEnv((void **) &env, JNI_VERSION_1_6);

    if (g_ctx.bindingsClz) {
        jmethodID methodId = env->GetMethodID(
                g_ctx.bindingsClz, "onTransactionCommit", "(Z)V");

        env->CallVoidMethod(g_ctx.bindingsObj, methodId, JNI_FALSE);
    }
}

JNIEXPORT
int powersync_init(sqlite3 *db, char **pzErrMsg, const sqlite3_api_routines *pApi) {
    sqlite3_initialize();

    sqlite3_update_hook(db, update_hook_callback, NULL);
    sqlite3_commit_hook(db, commit_hook, NULL);
    sqlite3_rollback_hook(db, rollback_hook, NULL);

    return SQLITE_OK;
}

JNIEXPORT
void JNICALL Java_com_powersync_DatabaseDriverFactory_setupSqliteBinding(JNIEnv *env, jobject thiz) {
    jclass clz = env->GetObjectClass(thiz);
    g_ctx.bindingsClz = (jclass) env->NewGlobalRef(clz);
    g_ctx.bindingsObj = env->NewGlobalRef(thiz);
}

}
