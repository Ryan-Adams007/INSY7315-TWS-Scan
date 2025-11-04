package za.tws.scan.net

// Request/response mirror your FastAPI
data class LoginRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val UserId: Int,
    val Name: String,
    val Email: String,
    val Role: String? = null
)

data class LoginResponse(
    val ok: Boolean,
    val access_token: String,
    val token_type: String,
    val user: UserDto
)