package co.powersync.demos

import kotlinx.datetime.Clock
import kotlin.random.Random

fun generateRandomPerson(): Pair<String, String> {
    val names =
        listOf("John", "Jane", "Bob", "Alice", "Charlie", "Megan", "Mike", "Sally", "Joe", "Jill")
    val domains = listOf("gmail.com", "yahoo.com", "hotmail.com", "outlook.com")

    val random = Random(
        Clock.System.now().toEpochMilliseconds()
    )

    val num = random.nextInt(names.size)

    val name = names[random.nextInt(names.size)]
    val domain = domains[random.nextInt(domains.size)]

    val email = "${name.lowercase()}${num + 1}@$domain"

    return Pair(name, email)
}