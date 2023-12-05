package co.powersync

class Greeting {
    private val platform = getPlatform()

    fun greet(db: Database?): String {
        if (db == null) return "Hello, ${platform.name}!"
        val players = db.getAllPlayers()
        val player = players.first()
        return "Hello, ${platform.name}! ${player.full_name}"
    }

//    fun greet(): String {
//        return "Hello, ${platform.name}!"
//    }
}