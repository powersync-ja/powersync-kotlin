package co.powersync.core

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform