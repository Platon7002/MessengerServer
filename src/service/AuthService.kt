package service

import data.Session
import data.User
import repository.SessionRepository
import repository.UserRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AuthService(
    private val users: UserRepository,
    private val sessions: SessionRepository
) {

    private val random = SecureRandom()
    private val usernameRule = Regex("[A-Za-z0-9_]{3,32}")

    fun register(username: String?, password: String?): User {
        val name = username?.trim().orEmpty()
        if (!usernameRule.matches(name)) {
            throw ServiceException(400, "Имя пользователя: 3–32 символа, только латинские буквы, цифры и _")
        }
        val pass = password.orEmpty()
        if (pass.length < 4 || pass.length > 100) {
            throw ServiceException(400, "Пароль должен быть длиной от 4 до 100 символов")
        }
        val salt = randomHex(16)
        val hash = hashPassword(pass, salt)
        return users.addIfAbsent(name, salt, hash)
            ?: throw ServiceException(400, "Пользователь «$name» уже существует")
    }

    fun login(username: String?, password: String?): String {
        val name = username?.trim().orEmpty()
        val pass = password.orEmpty()
        if (name.isEmpty() || pass.isEmpty()) {
            throw ServiceException(400, "Нужно передать username и password")
        }
        val user = users.findByUsername(name)

        if (user == null || !passwordMatches(pass, user)) {
            throw ServiceException(401, "Неверное имя пользователя или пароль")
        }
        val token = randomHex(24)
        sessions.add(Session(token, user.username, LocalDateTime.now().withNano(0)))
        return token
    }

    fun authenticate(token: String): String? = sessions.findByToken(token)?.username

    private fun passwordMatches(password: String, user: User): Boolean {
        val actual = hashPassword(password, user.salt).toByteArray()
        val expected = user.passwordHash.toByteArray()
        return MessageDigest.isEqual(actual, expected)
    }

    private fun hashPassword(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), 10_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
