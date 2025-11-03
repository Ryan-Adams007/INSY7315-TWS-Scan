package za.tws.scan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
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
import java.util.concurrent.TimeUnit

class PackingActivity : AppCompatActivity() {

    private val TAG = "PackingActivity"

    // --- Views ---
    private lateinit var edtItemSerial: TextInputEditText
    private lateinit var tilItemSerial: TextInputLayout
    private lateinit var txtQty: TextView
    private lateinit var chipPackageId: Chip
    private lateinit var btnScanPackage: MaterialButton
    private lateinit var btnAddToPackage: MaterialButton
    private lateinit var btnUndo: MaterialButton
    private lateinit var btnStartNewPackage: MaterialButton
    private lateinit var btnClearPackage: MaterialButton
    private lateinit var fabSealAndPrint: ExtendedFloatingActionButton
    private lateinit var recyclerPackageItems: RecyclerView
    private lateinit var minusBtn: MaterialButton
    private lateinit var plusBtn: MaterialButton
    private lateinit var recyclerStaged: RecyclerView
    private lateinit var txtStagedSummary: TextView
    private lateinit var txtPkgCount: TextView

    // --- Runtime state ---
    private var packingId: Int? = null
    private var packageNumber: String? = null
    private var stagingId: Int? = null
    private var qty: Int = 1

    // Items actually packed (from /pack/items)
    private data class PackItem(
        val packingItemId: Int?,
        val productId: Int?,
        val sku: String?,
        val name: String?,
        val quantity: Int
    )
    private val packedItems = mutableListOf<PackItem>()
    private lateinit var packedAdapter: RecyclerView.Adapter<SimpleVH>

    // Staged requirements (what must be packed)
    private data class StagedLine(
        val productId: Int?,
        val sku: String?,
        val name: String?,
        val required: Int
    )
    private val stagedLines = mutableListOf<StagedLine>()
    private lateinit var stagedAdapter: RecyclerView.Adapter<SimpleVH>

    // --- API ---
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
        setContentView(R.layout.activity_packing)

        // AppBar
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val appBar: AppBarLayout = findViewById(R.id.appbar)
        setSupportActionBar(toolbar)
        window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, view.paddingBottom)
            insets
        }

        // Bind views
        tilItemSerial = findViewById(R.id.tilItemSerial)
        edtItemSerial = findViewById(R.id.edtItemSerial)
        txtQty = findViewById(R.id.txtQty)
        chipPackageId = findViewById(R.id.chipPackageId)
        btnScanPackage = findViewById(R.id.btnScanPackage)
        btnAddToPackage = findViewById(R.id.btnAddToPackage)
        btnUndo = findViewById(R.id.btnUndo)
        btnStartNewPackage = findViewById(R.id.btnStartNewPackage)
        btnClearPackage = findViewById(R.id.btnClearPackage)
        fabSealAndPrint = findViewById(R.id.fabSealAndPrint)
        recyclerPackageItems = findViewById(R.id.recyclerPackageItems)
        recyclerStaged = findViewById(R.id.recyclerStaged)
        txtStagedSummary = findViewById(R.id.txtStagedSummary)
        txtPkgCount = findViewById(R.id.txtPkgCount)

        // qty steppers (includes)
        val minusInclude: View = findViewById(R.id.btnQtyMinus)
        val plusInclude: View = findViewById(R.id.btnQtyPlus)
        minusBtn = minusInclude.findViewById(R.id.roundMinusButton)
        plusBtn = plusInclude.findViewById(R.id.roundPlusButton)

        qty = txtQty.text.toString().toIntOrNull() ?: 1
        txtQty.text = qty.toString()

        // --- Staged adapter (What to pack) ---
        recyclerStaged.layoutManager = LinearLayoutManager(this)
        stagedAdapter = object : RecyclerView.Adapter<SimpleVH>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SimpleVH {
                val tv = TextView(parent.context).apply {
                    setPadding(24, 18, 24, 18)
                    setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
                }
                return SimpleVH(tv)
            }
            override fun getItemCount(): Int = if (stagedLines.isEmpty()) 1 else stagedLines.size
            override fun onBindViewHolder(holder: SimpleVH, position: Int) {
                val tv = holder.itemView as TextView
                if (stagedLines.isEmpty()) {
                    tv.text = "Nothing staged yet"
                    tv.setTextColor(ContextCompat.getColor(tv.context, R.color.textSecondary))
                } else {
                    val s = stagedLines[position]
                    val packedSoFar = aggregatePacked()[s.productId] ?: 0
                    val remaining = (s.required - packedSoFar).coerceAtLeast(0)
                    tv.text = "${s.sku ?: "-"} — ${s.name ?: "-"}   required: ${s.required} • packed: $packedSoFar • remaining: $remaining"
                    tv.setTextColor(ContextCompat.getColor(tv.context,
                        if (remaining == 0) R.color.textSecondary else R.color.textPrimary))
                }
            }
        }
        recyclerStaged.adapter = stagedAdapter

        // --- Packed items adapter (Items in this package) ---
        recyclerPackageItems.layoutManager = LinearLayoutManager(this)
        packedAdapter = object : RecyclerView.Adapter<SimpleVH>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SimpleVH {
                val tv = TextView(parent.context).apply {
                    setPadding(24, 18, 24, 18)
                    setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
                }
                return SimpleVH(tv)
            }
            override fun getItemCount(): Int = if (packedItems.isEmpty()) 1 else packedItems.size
            override fun onBindViewHolder(holder: SimpleVH, position: Int) {
                val tv = holder.itemView as TextView
                if (packedItems.isEmpty()) {
                    tv.text = "No items yet"
                    tv.setTextColor(ContextCompat.getColor(tv.context, R.color.textSecondary))
                } else {
                    val it = packedItems[position]
                    tv.text = "${it.sku ?: "-"} — ${it.name ?: "-"}   ×${it.quantity}"
                    tv.setTextColor(ContextCompat.getColor(tv.context, R.color.textPrimary))
                }
            }
        }
        recyclerPackageItems.adapter = packedAdapter

        wireUi()
        claimNextOrStartFresh()
    }

    private fun wireUi() {
        minusBtn.setOnClickListener {
            qty = (qty - 1).coerceAtLeast(1)
            txtQty.text = qty.toString()
        }
        plusBtn.setOnClickListener {
            qty = (qty + 1).coerceAtMost(9999)
            txtQty.text = qty.toString()
        }
        tilItemSerial.setEndIconOnClickListener {
            Toast.makeText(this, "Open scanner for item serial…", Toast.LENGTH_SHORT).show()
        }
        btnScanPackage.setOnClickListener {
            startOrSetPackage(packageNumber = null) // server generates next PKG-xxxx
        }
        btnStartNewPackage.setOnClickListener {
            startOrSetPackage(packageNumber = null)
        }
        btnAddToPackage.setOnClickListener { addCurrentSerial() }
        btnUndo.setOnClickListener { undoLast() }
        btnClearPackage.setOnClickListener { clearPackage() }
        fabSealAndPrint.setOnClickListener { sealPackage() }
    }

    /** Claim next staged job and ensure a package is ready. Also pulls staged lines for UI. */
    private fun claimNextOrStartFresh() {
        val prefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        val packedBy = prefs.getInt("userId", -1)
        if (packedBy <= 0) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                // 1) Claim next job
                val res = api.claimNext(packedBy = packedBy, packageNumber = null)

                // NOTE: server returns TitleCase keys
                stagingId     = (res["StagingId"] as? Number)?.toInt()
                packingId     = (res["PackingId"] as? Number)?.toInt()
                packageNumber = res["PackageNumber"] as? String

                // 2) Load “What to pack” lines from dedicated endpoint
                stagedLines.clear()
                if (stagingId != null) {
                    val lineRows = api.stagingGetLines(stagingId!!)
                    for (m in lineRows) {
                        val productId  = (m["ProductId"] as? Number)?.toInt()
                        val sku        = m["Sku"] as? String
                        val name       = m["Name"] as? String
                        val qtyNeeded  = (m["Qty"] as? Number)?.toInt()
                            ?: (m["Quantity"] as? Number)?.toInt()
                            ?: (m["Required"] as? Number)?.toInt()
                            ?: 0
                        stagedLines.add(StagedLine(productId, sku, name, qtyNeeded))
                    }
                }
                stagedAdapter.notifyDataSetChanged()
                updateStagedSummary()

                // 3) Ensure we have a package (fallback if claim didn't include one)
                if (packingId == null || packageNumber.isNullOrBlank()) {
                    val pack = api.packStartOrSet(null)
                    packingId     = (pack["PackingId"] as? Number)?.toInt()
                    packageNumber =  pack["PackageNumber"] as? String
                }

                // 4) Update UI + fetch already-packed items
                chipPackageId.text = packageNumber ?: "No package set"
                Log.i(TAG, "Claimed staging=$stagingId, packing=$packingId, pkg=$packageNumber")
                refreshPackedItems()

            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP ${e.code()} claimNext: $body", e)
                startOrSetPackage(packageNumber = null)
            } catch (e: Exception) {
                Log.e(TAG, "claimNext error: ${e.message}", e)
                startOrSetPackage(packageNumber = null)
            }
        }
    }

    /** Ensure or create a package (server generates PKG-xxxx when null). */
    private fun startOrSetPackage(packageNumber: String?) {
        lifecycleScope.launch {
            try {
                val res = api.packStartOrSet(packageNumber)
                packingId = (res["PackingId"] as? Number)?.toInt()
                this@PackingActivity.packageNumber = (res["PackageNumber"] as? String)
                chipPackageId.text = this@PackingActivity.packageNumber ?: "No package set"
                Log.i(TAG, "Package ready: id=$packingId num=${this@PackingActivity.packageNumber}")
                refreshPackedItems()
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP ${e.code()} startOrSet: $body", e)
                Toast.makeText(this@PackingActivity, "Package error: ${e.code()}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "startOrSet error: ${e.message}", e)
                Toast.makeText(this@PackingActivity, "Package error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addCurrentSerial() {
        val pid = packingId ?: return Toast.makeText(this, "No package set", Toast.LENGTH_SHORT).show()
        val serial = edtItemSerial.text?.toString()?.trim().orEmpty()
        if (serial.isEmpty()) {
            tilItemSerial.error = "Enter or scan a serial"
            return
        } else tilItemSerial.error = null

        val q = txtQty.text.toString().toIntOrNull() ?: 1

        lifecycleScope.launch {
            try {
                val res = api.packAddItem(
                    packingId = pid,
                    barcodeOrSerial = serial,
                    qty = q
                )
                Log.i(TAG, "Added to package: $res")
                Toast.makeText(this@PackingActivity, "Added $serial ×$q", Toast.LENGTH_SHORT).show()
                edtItemSerial.setText("")
                qty = 1
                txtQty.text = "1"
                refreshPackedItems()
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP ${e.code()} add-item: $body", e)
                Toast.makeText(this@PackingActivity, "Add failed: ${e.code()}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "add-item error: ${e.message}", e)
                Toast.makeText(this@PackingActivity, "Add failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun undoLast() {
        val pid = packingId ?: return Toast.makeText(this, "No package set", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                api.packUndoLast(pid)
                refreshPackedItems()
            } catch (e: Exception) {
                Log.e(TAG, "undo error: ${e.message}", e)
                Toast.makeText(this@PackingActivity, "Undo failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearPackage() {
        val pid = packingId ?: return Toast.makeText(this, "No package set", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                api.packClear(pid)
                refreshPackedItems()
            } catch (e: Exception) {
                Log.e(TAG, "clear error: ${e.message}", e)
                Toast.makeText(this@PackingActivity, "Clear failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Validate and seal. Blocks if staged requirements aren’t fully met. */
    private fun sealPackage() {
        val pid = packingId ?: return Toast.makeText(this, "No package set", Toast.LENGTH_SHORT).show()

        // Hard validation before server call
        val (ok, msg) = canSeal()
        if (!ok) {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                api.packSeal(pid)
                Toast.makeText(this@PackingActivity, "Package sealed ✅", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this@PackingActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "seal error: ${e.message}", e)
                Toast.makeText(this@PackingActivity, "Seal failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Pulls the server list of items currently added to the package. */
    private fun refreshPackedItems() {
        val pid = packingId ?: return
        lifecycleScope.launch {
            try {
                val list = api.packGetItems(pid) // List<Map<String, Any>>
                packedItems.clear()
                var lineCount = 0
                for (m in list) {
                    val row = PackItem(
                        packingItemId = (m["PackingItemId"] as? Number)?.toInt(),
                        productId = (m["ProductId"] as? Number)?.toInt(),
                        sku = m["Sku"] as? String,
                        name = m["Name"] as? String,
                        quantity = (m["Quantity"] as? Number)?.toInt() ?: 0
                    )
                    packedItems.add(row)
                    lineCount++
                }
                packedAdapter.notifyDataSetChanged()
                txtPkgCount.text = "Items: $lineCount"
                updateStagedSummary()
                Log.d(TAG, "Packed items loaded: ${packedItems.size}")
            } catch (e: Exception) {
                Log.e(TAG, "packGetItems error: ${e.message}", e)
            }
        }
    }

    /** Build a productId -> packedQty map. */
    private fun aggregatePacked(): Map<Int?, Int> {
        val map = mutableMapOf<Int?, Int>()
        for (pi in packedItems) {
            val key = pi.productId
            map[key] = (map[key] ?: 0) + pi.quantity
        }
        return map
    }

    /** Update “What to pack” summary line and refresh staged list visuals. */
    private fun updateStagedSummary() {
        val packedMap = aggregatePacked()
        var totalRequired = 0
        var totalRemaining = 0
        for (s in stagedLines) {
            totalRequired += s.required
            val packed = packedMap[s.productId] ?: 0
            totalRemaining += (s.required - packed).coerceAtLeast(0)
        }
        txtStagedSummary.text = "${stagedLines.size} lines, $totalRemaining remaining (of $totalRequired)"
        stagedAdapter.notifyDataSetChanged()
    }

    /** Returns (ok, message). Blocks sealing if:
     *  - any staged line has remaining > 0
     *  - there are extra items not on the staged list
     */
    private fun canSeal(): Pair<Boolean, String> {
        // If you ever want to enforce “must have a staging”, uncomment:
        // if (stagingId == null) return false to "No staged job claimed."

        val requiredMap = mutableMapOf<Int?, Int>()
        for (s in stagedLines) requiredMap[s.productId] = (requiredMap[s.productId] ?: 0) + s.required

        val packedMap = aggregatePacked()

        // 1) Missing quantities
        val missing = mutableListOf<String>()
        for (s in stagedLines) {
            val packed = packedMap[s.productId] ?: 0
            val rem = s.required - packed
            if (rem > 0) {
                missing.add("${s.sku ?: s.name ?: "#${s.productId}"} (need $rem more)")
            }
        }
        if (missing.isNotEmpty()) {
            return false to "Still missing: ${missing.joinToString(", ")}"
        }

        // 2) Extras not in staged
        val stagedIds = requiredMap.keys.filterNotNull().toSet()
        val extras = mutableListOf<String>()
        for ((pid, q) in packedMap) {
            val id = pid ?: continue
            if (!stagedIds.contains(id)) {
                val any = packedItems.firstOrNull { it.productId == id }
                extras.add("${any?.sku ?: any?.name ?: "#$id"} ×$q")
            }
        }
        if (extras.isNotEmpty()) {
            return false to "These aren’t on the list: ${extras.joinToString(", ")}"
        }

        return true to "OK"
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

    private class SimpleVH(view: View) : RecyclerView.ViewHolder(view)
}