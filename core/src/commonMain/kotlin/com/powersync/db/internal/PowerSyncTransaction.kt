package com.powersync.db.internal

public interface PowerSyncTransaction : ConnectionContext

internal class PowerSyncTransactionImpl(
    context: ConnectionContext,
) : PowerSyncTransaction,
    ConnectionContext by context
