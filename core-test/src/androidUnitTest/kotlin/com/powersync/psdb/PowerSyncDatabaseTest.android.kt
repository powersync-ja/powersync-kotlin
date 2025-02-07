package com.powersync.psdb

import com.powersync.DatabaseDriverFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
actual abstract class RobolectricTest actual constructor()

actual val factory: DatabaseDriverFactory
    get() = DatabaseDriverFactory(RuntimeEnvironment.getApplication())