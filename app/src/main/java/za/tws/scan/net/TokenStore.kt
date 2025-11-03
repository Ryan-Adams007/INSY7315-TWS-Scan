package za.tws.scan.net

import android.content.Context
import androidx.core.content.edit

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun save(token: String, userName: String, userEmail: String) {
        prefs.edit {
            putString("token", token)
            putString("name", userName)
            putString("email", userEmail)
        }
    }

    fun token(): String? = prefs.getString("token", null)
    fun clear() = prefs.edit { clear() }
}