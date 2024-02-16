#include <jni.h>
#include <sqlite3.h>

JavaVM *g_JavaVM = NULL;
jobject g_listener = NULL;
jmethodID g_methodID = NULL;

void update_hook_callback(void *p, int type, char const *db, char const *tbl, sqlite3_int64 rowid) {
    JNIEnv *env;
    g_JavaVM->AttachCurrentThread(&env, NULL);

    env->CallVoidMethod(g_listener, g_methodID, type, env->NewStringUTF(db), env->NewStringUTF(tbl),
                        rowid);

    g_JavaVM->DetachCurrentThread();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_powersync_SqliteBindings_registerUpdateListener(JNIEnv *env, jobject thiz,
                                                         jlong dbPointer) {
    env->GetJavaVM(&g_JavaVM);
    g_listener = env->NewGlobalRef(thiz);
    jclass cls = env->GetObjectClass(thiz);
    g_methodID = env->GetMethodID(cls, "onUpdate", "(ILjava/lang/String;Ljava/lang/String;J)V");

    sqlite3 *db = (sqlite3 *) dbPointer;
    sqlite3_update_hook(db, update_hook_callback, NULL);
}

