package com.powersync.sync

import platform.UIKit.UIDevice

internal actual fun getOS(): String {
    val current = UIDevice.currentDevice
    val version = current.systemVersion

    return "ios $version"
}
