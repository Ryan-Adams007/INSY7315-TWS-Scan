package za.tws.scan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import za.tws.scan.net.ApiService
import za.tws.scan.net.PickingSession
import za.tws.scan.net.RecentScansResponse
import za.tws.scan.net.ScanItem
import java.util.concurrent.TimeUnit

class PickingActivity : AppCompatActivity() {

    private val TAG = "PickingActivity"

    private lateinit var tilSerial: TextInputLayout
    private lateinit var edtSerial: TextInputEditText
    private lateinit var txtQty: TextView
    private lateinit var btnMinus: MaterialButton
    private lateinit var btnPlus: MaterialButton
    private lateinit var btnAdd: MaterialButton
    private lateinit var fabDone: ExtendedFloatingActionButton
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ScansAdapter

    private var qty = 1
    private var sessionId: Int? = null

    // Retrofit with Authorization + X-API-Key
    private val api: ApiService by lazy {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

        val bearerInterceptor = Interceptor { chain ->
            val prefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
            val token = prefs.getString("access_token", null)
            val req = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else chain.request()
            chain.proceed(req)
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
            .addInterceptor(bearerInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(ok)
            .build()
            .create(ApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_picking)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val appBar: AppBarLayout = findViewById(R.id.appbar)
        setSupportActionBar(toolbar)

        window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, view.paddingBottom)
            insets
        }

        // --- View refs ---
        tilSerial = findViewById(R.id.tilSerial)
        edtSerial = findViewById(R.id.edtSerial)
        txtQty = findViewById(R.id.txtQty)
        btnMinus = findViewById(R.id.roundMinusButton)
        btnPlus  = findViewById(R.id.roundPlusButton)
        btnAdd = findViewById(R.id.btnScanConfirm)
        fabDone = findViewById(R.id.fabCompletePick)
        recycler = findViewById(R.id.recyclerScans)

        // Recycler setup
        adapter = ScansAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        qty = txtQty.text.toString().toIntOrNull() ?: 1
        txtQty.text = qty.toString()

        fun tap(v: View, block: () -> Unit) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            v.animate()
                .scaleX(0.94f).scaleY(0.94f).setDuration(70)
                .withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    block()
                }.start()
        }

        btnMinus.setOnClickListener { v ->
            tap(v) {
                if (qty > 1) qty--
                txtQty.text = qty.toString()
            }
        }

        btnPlus.setOnClickListener { v ->
            tap(v) {
                qty++
                txtQty.text = qty.toString()
            }
        }

        tilSerial.setEndIconOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(this, "Open scanner…", Toast.LENGTH_SHORT).show()
        }

        btnAdd.setOnClickListener { v -> tap(v) { onAddScanPressed() } }
        fabDone.setOnClickListener { v -> tap(v) { onCompletePressed() } }

        // Start session and load recents
        startPickingSession()
    }

    // ---- API calls ----

    private fun startPickingSession() {
        val prefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        val userId = prefs.getInt("userId", -1)
        val token = prefs.getString("access_token", null)

        if (token.isNullOrBlank() || userId <= 0) {
            Log.e(TAG, "Missing auth (token=$token, userId=$userId). Redirecting to login.")
            startActivity(Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        Log.d(TAG, "Starting picking session for userId=$userId")
        lifecycleScope.launch {
            try {
                val session: PickingSession = api.startPick(userId)
                sessionId = session.SessionId
                Log.i(TAG, "Picking session started: id=$sessionId")
                Toast.makeText(this@PickingActivity, "Session started (#$sessionId)", Toast.LENGTH_SHORT).show()

                fetchRecent()   // <-- load initial list
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP ${e.code()} starting session: $body", e)
                Toast.makeText(this@PickingActivity, "Start failed: ${e.code()}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Network error starting session: ${e.message}", e)
                Toast.makeText(this@PickingActivity, "Start failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onAddScanPressed() {
        val sId = sessionId ?: run {
            Toast.makeText(this, "No session yet — retry in a moment", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "AddScan pressed without an active session")
            return
        }
        val serial = edtSerial.text?.toString()?.trim().orEmpty()
        if (serial.isEmpty()) {
            tilSerial.error = "Serial required"
            return
        }
        tilSerial.error = null

        Log.d(TAG, "Adding scan: sessionId=$sId serial='$serial' qty=$qty")
        lifecycleScope.launch {
            try {
                api.addScan(sessionId = sId, barcodeOrSerial = serial, qty = qty)
                Log.i(TAG, "Scan added OK")
                Toast.makeText(this@PickingActivity, "Added $serial ×$qty", Toast.LENGTH_SHORT).show()
                edtSerial.setText("")
                qty = 1
                txtQty.text = "1"

                fetchRecent()   // <-- refresh list after add
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP ${e.code()} add-scan: $body", e)
                Toast.makeText(this@PickingActivity, "Add failed: ${e.code()}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Network error add-scan: ${e.message}", e)
                Toast.makeText(this@PickingActivity, "Add failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fetchRecent() {
        val sId = sessionId ?: return
        lifecycleScope.launch {
            try {
                val recents: RecentScansResponse = api.recentScans(sId, top = 25)
                Log.d(TAG, "Recent: ${recents.items.size} items")
                adapter.submit(recents.items)
            } catch (e: Exception) {
                Log.e(TAG, "recentScans failed: ${e.message}", e)
            }
        }
    }

    private fun onCompletePressed() {
        val sId = sessionId ?: run {
            Toast.makeText(this, "No active session", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Complete pressed without an active session")
            return
        }

        Log.d(TAG, "Completing pick: sessionId=$sId")
        lifecycleScope.launch {
            try {
                val resp = api.completePick(sessionId = sId)
                Log.i(TAG, "Complete OK: $resp")
                Toast.makeText(this@PickingActivity, "Pick completed ✅", Toast.LENGTH_SHORT).show()

                // NEW: stage this session for packing
                val staged = api.stageFromPick(sId)
                val stagingId = (staged["StagingId"] as? Number)?.toInt()
                    ?: (staged["stagingId"] as? Number)?.toInt()

                Log.d(TAG, "Staged for pack: stagingId=$stagingId")

                // Jump to PackActivity with stagingId (and userId for claim if needed)
                val userId = getSharedPreferences("auth", Context.MODE_PRIVATE)
                    .getInt("userId", -1)

                startActivity(
                    Intent(this@PickingActivity, PackingActivity::class.java).apply {
                        putExtra("stagingId", stagingId ?: -1)
                        putExtra("userId", userId)
                    }.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP ${e.code()} complete/stage: $body", e)
                Toast.makeText(this@PickingActivity, "Complete/stage failed: ${e.code()}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Network error complete/stage: ${e.message}", e)
                Toast.makeText(this@PickingActivity, "Complete/stage failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_global, menu)
        val white = ContextCompat.getColor(this, R.color.white)
        for (i in 0 until menu.size()) menu.getItem(i).icon?.setTint(white)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_back -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
                true
            }
            R.id.action_menu -> {
                Toast.makeText(this, "Menu clicked", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- Minimal adapter for the list of recent scans ---
    private class ScansAdapter : RecyclerView.Adapter<ScansVH>() {
        private val items = mutableListOf<ScanItem>()

        fun submit(newItems: List<ScanItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ScansVH {
            val v = layoutInflater(parent.context).inflate(parent)
            return ScansVH(v)
        }

        override fun onBindViewHolder(holder: ScansVH, position: Int) {
            val it = items[position]
            holder.bind(it)
        }

        override fun getItemCount() = items.size

        private fun layoutInflater(ctx: Context) =
            android.view.LayoutInflater.from(ctx)

        private fun android.view.LayoutInflater.inflate(parent: android.view.ViewGroup) =
            this.inflate(R.layout.item_scan_row, parent, false)
    }

    private class ScansVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val line1: TextView = itemView.findViewById(R.id.txtLine1)
        private val line2: TextView = itemView.findViewById(R.id.txtLine2)

        fun bind(item: ScanItem) {
            line1.text = "${item.BarcodeOrSerial}  ×${item.Qty}"
            line2.text = item.ScannedAt
        }
    }
}