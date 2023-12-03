package co.powersync

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform