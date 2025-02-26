# If the app calls the JNI method to initialize driver bindings, keep that method
# (so that it can be linked through JNI) and the other methods called from native
# code.
-if class com.powersync.DatabaseDriverFactory {
    private void setupSqliteBinding();
}
-keep class com.powersync.DatabaseDriverFactory {
    private void setupSqliteBinding();
    private void onTableUpdate(java.lang.String);
    private void onTransactionCommit(boolean);
}
