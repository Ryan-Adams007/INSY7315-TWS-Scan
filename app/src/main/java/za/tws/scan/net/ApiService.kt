package za.tws.scan.net

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {
    @Headers("Content-Type: application/json")
    @POST("auth/login")
    suspend fun login(
        @Body body: LoginRequest
    ): LoginResponse
}