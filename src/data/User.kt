package data

data class User(
    val id: Int,
    val username: String,
    val salt: String,
    val passwordHash: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "username" to username,
        "salt" to salt,
        "passwordHash" to passwordHash
    )
}
