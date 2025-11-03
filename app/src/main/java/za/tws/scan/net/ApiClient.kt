package za.tws.scan.net

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import za.tws.scan.BuildConfig
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * Create a Retrofit ApiService that always sends:
     *  - X-API-Key (from BuildConfig.API_KEY)
     *  - Authorization: Bearer <token>  (when tokenProvider returns a non-blank value)
     */
    fun create(tokenProvider: () -> String?): ApiService {
        // Log full requests/responses (disable/adjust in release if needed)
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Adds Authorization header when available
        val bearerInterceptor = Interceptor { chain ->
            val token = tokenProvider()?.trim()
            val req = chain.request().newBuilder().apply {
                if (!token.isNullOrEmpty()) addHeader("Authorization", "Bearer $token")
            }.build()
            chain.proceed(req)
        }

        // Always add API key header (server expects X-API-Key)
        val apiKeyInterceptor = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("X-API-Key", BuildConfig.API_KEY)
                // Optional: also send common alias just in case server middleware is lenient
                .addHeader("X-Api-Key", BuildConfig.API_KEY)
                .build()
            chain.proceed(req)
        }

        val ok = OkHttpClient.Builder()
            .addInterceptor(logger)
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(bearerInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // e.g. http://10.0.2.2:8000/
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(ok)
            .build()
            .create(ApiService::class.java)
    }
}