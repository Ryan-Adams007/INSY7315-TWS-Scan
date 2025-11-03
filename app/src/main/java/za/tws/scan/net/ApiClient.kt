package za.tws.scan.net

import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import za.tws.scan.BuildConfig

object ApiClient {

    fun create(tokenProvider: () -> String?): ApiService {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val req = chain.request()
            val token = tokenProvider()
            val newReq = if (!token.isNullOrBlank()) {
                req.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else req
            chain.proceed(newReq)
        }

        val ok = OkHttpClient.Builder()
            .addInterceptor(logger)
            .addInterceptor(authInterceptor)
            .build()

        val moshi = Moshi.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // comes from BuildConfig
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(ok)
            .build()

        return retrofit.create(ApiService::class.java)
    }
}