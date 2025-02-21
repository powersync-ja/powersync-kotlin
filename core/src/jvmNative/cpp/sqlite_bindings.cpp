#include <jni.h>
#include <sqlite3.h>
#include <string>
#include <cstring>

extern "C" {

typedef struct hooks {
    JavaVM *jvm;
    jclass update_hook_receiver_class;
    jobject update_hook_receiver_instance;
} Hooks;

JNIEXPORT
jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;  // JNI version not supported.
    }

    return JNI_VERSION_1_6;
}

static void update_hook_callback(void *pData, int opCode, char const *pDbName, char const *pTableName, sqlite3_int64 iRow) {

}

static int commit_hook(void *pool) {
    return 0;
}

static void rollback_hook(void *pool) {

}

JNIEXPORT
int powersync_init(sqlite3 *db, char **pzErrMsg, const sqlite3_api_routines *pApi) {
    sqlite3_initialize();

    return SQLITE_OK;
}

JNIEXPORT
void JNICALL Java_com_powersync_DatabaseDriverFactory_registerUpdates(JNIEnv *env, jlong dbPtr, jobject receiver) {
    jclass clz = env->GetObjectClass(receiver);

    sqlite3 *db = (sqlite3*) dbPtr;
    Hooks *hooks = static_cast<Hooks*>(calloc(1, sizeof(Hooks)));
    env->GetJavaVM(&hooks->jvm);
    hooks->update_hook_receiver_class = (jclass) env->NewGlobalRef(clz);
    hooks->update_hook_receiver_instance = env->NewGlobalRef(receiver);

    sqlite3_update_hook(db, update_hook_callback, hooks);
    sqlite3_commit_hook(db, commit_hook, hooks);
    sqlite3_rollback_hook(db, rollback_hook, hooks);
}

JNIEXPORT
void JNICALL Java_com_powersync_DatabaseDriverFactory_unregisterUpdates(JNIEnv *env, jlong dbPtr, jobject receiver) {

}

}
