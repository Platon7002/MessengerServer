package server

class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,      // имена заголовков в нижнем регистре
    val body: String
) {
    @Volatile
    var authenticatedUser: String? = null
}
