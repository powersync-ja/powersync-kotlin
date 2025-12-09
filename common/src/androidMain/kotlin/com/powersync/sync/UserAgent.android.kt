package com.powersync.sync

import android.os.Build
import com.powersync.build.LIBRARY_VERSION

internal actual fun userAgent(): String = "powersync-kotlin/$LIBRARY_VERSION android/${Build.VERSION.SDK_INT}"
