package za.tws.scan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import za.tws.scan.BuildConfig
import za.tws.scan.net.ApiService
import za.tws.scan.net.LoginRequest
import za.tws.scan.net.LoginResponse
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progress: CircularProgressIndicator

    private val TAG = "LoginActivity"

    // Retrofit for auth (adds required X-API-Key)
    private val api: ApiService by lazy {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val apiKeyInterceptor = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("X-API-Key", BuildConfig.API_KEY)
                .build()
            chain.proceed(req)
        }

        val ok = OkHttpClient.Builder()
            .addInterceptor(logger)
            .addInterceptor(apiKeyInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // e.g. http://10.0.2.2:8000/
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(ok)
            .build()
            .create(ApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_login)

        emailInput    = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        btnLogin      = findViewById(R.id.btnLogin)
        progress      = findViewById(R.id.progress)

        Log.d(TAG, "onCreate: Login UI ready; baseUrl=${BuildConfig.API_BASE_URL}")

        btnLogin.setOnClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString()?.trim().orEmpty()
            Log.d(TAG, "Sign in tapped: email='$email' pwdLen=${password.length}")

            if (email.isEmpty() || password.isEmpty()) {
                Log.w(TAG, "Validation failed: empty email or password")
                return@setOnClickListener
            }
            performLogin(email, password)
        }
    }

    private fun performLogin(email: String, password: String) {
        setLoading(true)
        Log.d(TAG, "performLogin() -> POST /auth/login")
        lifecycleScope.launch {
            try {
                val resp: LoginResponse = api.login(LoginRequest(email, password))
                Log.d(TAG, "login response: ok=${resp.ok}, user=${resp.user.Email}, tokenPrefix=${resp.access_token.take(12)}")
                if (resp.ok && resp.access_token.isNotBlank()) {
                    val userId = resp.user.UserId
                    if (userId <= 0) {
                        Log.w(TAG, "Login returned invalid userId=$userId")
                    }
                    saveAuth(
                        token = resp.access_token,
                        name = resp.user.Name,
                        email = resp.user.Email,
                        userId = userId
                    )
                    Log.i(TAG, "Login success for ${resp.user.Email} (userId=$userId)")
                    goToMain() // navigate on success
                } else {
                    Log.e(TAG, "Login failed: ok=${resp.ok} emptyToken=${resp.access_token.isBlank()}")
                }
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP ${e.code()} ${e.message()} body=$body", e)
            } catch (e: Exception) {
                Log.e(TAG, "Network error: ${e.message}", e)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        Log.d(TAG, "Navigated to MainActivity")
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        Log.d(TAG, "setLoading($loading)")
    }

    // UPDATED: also persist userId for picking session API
    private fun saveAuth(token: String, name: String, email: String, userId: Int) {
        getSharedPreferences("auth", Context.MODE_PRIVATE).edit()
            .putString("access_token", token)
            .putString("name", name)
            .putString("email", email)
            .putInt("userId", userId)
            .apply()
        Log.d(TAG, "Auth saved for $email (userId=$userId, tokenLen=${token.length})")
    }
}