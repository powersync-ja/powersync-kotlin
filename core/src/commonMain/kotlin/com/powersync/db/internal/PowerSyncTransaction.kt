package com.powersync.db.internal

public interface PowerSyncTransaction : ConnectionContext

public class PowerSyncTransactionImpl(
    context: ConnectionContext,
) : PowerSyncTransaction,
    ConnectionContext by context
