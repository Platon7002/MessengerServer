import app.Application

const val DEFAULT_PORT = 8080

fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: DEFAULT_PORT
    Application(port).run()
}
