package za.tws.scan

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.tws.scan.net.ApiClient
import za.tws.scan.net.ApiService
import za.tws.scan.net.StockItem as NetStockItem
import za.tws.scan.net.StockListResponse
import za.tws.scan.net.StockSession

class StockTakingActivity : AppCompatActivity() {

    // --- Views you already had ---
    private var recycler: RecyclerView? = null
    private var scanBtn: MaterialButton? = null
    private var tilSearch: TextInputLayout? = null
    private var edtSearch: TextInputEditText? = null

    // Current-product card views (from your XML)
    private var txtProductTitle: TextView? = null
    private var txtExpected: TextView? = null
    private var txtCounted: TextView? = null
    private var progressCount: LinearProgressIndicator? = null
    private var tilScan: TextInputLayout? = null
    private var edtScan: TextInputEditText? = null
    private var btnAddCount: MaterialButton? = null
    private var btnUndoCount: MaterialButton? = null

    // --- Data for your adapter ---
    private val allItems = mutableListOf<StockItem>()
    private val visibleItems = mutableListOf<StockItem>()
    private var adapter: StockAdapter? = null

    // --- API / session ---
    private lateinit var api: ApiService
    private var stockTakeId: Int? = null
    private val userId: Int by lazy { intent.getIntExtra("USER_ID", 6) } // default for local dev

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_stock_taking)

        // Toolbar/AppBar
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val appBar: AppBarLayout = findViewById(R.id.appbar)
        setSupportActionBar(toolbar)
        // Ensure no subtitle is shown under the main title
        supportActionBar?.subtitle = null

        window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Bind list/search you already had
        recycler = findViewById(R.id.recyclerStock)
        scanBtn = findViewById(R.id.btnScan) // optional include in your layout
        tilSearch = findViewById(R.id.tilSearch)
        edtSearch = findViewById(R.id.edtSearch)

        // Bind current-product card controls from your XML
        txtProductTitle = findViewById(R.id.txtProductTitle)
        txtExpected = findViewById(R.id.txtExpected)
        txtCounted = findViewById(R.id.txtCounted)
        progressCount = findViewById(R.id.progressCount)
        tilScan = findViewById(R.id.tilScan)
        edtScan = findViewById(R.id.edtScan)
        btnAddCount = findViewById(R.id.btnAddCount)
        btnUndoCount = findViewById(R.id.btnUndoCount)

        // API client (X-API-Key handled by ApiClient interceptors)
        api = ApiClient.create { null }

        // Recycler kept as-is, but row taps call the API now
        recycler?.let { rv ->
            rv.layoutManager = LinearLayoutManager(this)
            adapter = StockAdapter(visibleItems) { item, action ->
                when (action) {
                    RowAction.Increment -> addCountBySku(item.sku)
                    RowAction.Decrement -> undoLast()
                }
            }
            rv.adapter = adapter
        }

        // Scan round include (if you later wire camera)
        scanBtn?.setOnClickListener {
            Toast.makeText(this, "Open scanner…", Toast.LENGTH_SHORT).show()
        }

        // Search polish (unchanged)
        tilSearch?.isEndIconVisible = false
        tilSearch?.setEndIconOnClickListener { edtSearch?.setText("") }
        edtSearch?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilSearch?.isEndIconVisible = !s.isNullOrBlank()
                filterList(s?.toString().orEmpty())
            }
        })

        // Add/Undo buttons on the current-product card
        btnAddCount?.setOnClickListener {
            val code = edtScan?.text?.toString()?.trim().orEmpty()
            if (code.isBlank()) {
                Toast.makeText(this, "Scan or enter a barcode/SKU", Toast.LENGTH_SHORT).show()
            } else {
                addCountByBarcode(code)
            }
        }
        btnUndoCount?.setOnClickListener { undoLast() }

        // Start server session and load initial list
        startSession()
    }

    /* ---------------------------- API wiring ---------------------------- */

    private fun startSession() {
        lifecycleScope.launch {
            try {
                val session: StockSession = withContext(Dispatchers.IO) {
                    api.stockStart(userId = userId, name = null)
                }
                stockTakeId = session.StockTakeId

                // Removed the subtitle so nothing renders under the title
                // supportActionBar?.subtitle = session.Name

                loadItems()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@StockTakingActivity, "Failed to start stock take", Toast.LENGTH_LONG).show()
                // Fallback demo data
                seedDemoData()
            }
        }
    }

    private fun loadItems(search: String? = null) {
        val id = stockTakeId ?: return
        lifecycleScope.launch {
            try {
                val resp: StockListResponse = withContext(Dispatchers.IO) {
                    api.stockListItems(stockTakeId = id, search = search)
                }
                allItems.clear()
                allItems += resp.items.map { it.toViewModel() }
                filterList(edtSearch?.text?.toString().orEmpty())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@StockTakingActivity, "Failed to load items", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Add by barcode from the card input
    private fun addCountByBarcode(code: String) {
        val id = stockTakeId ?: return
        lifecycleScope.launch {
            try {
                val updatedNet: NetStockItem = withContext(Dispatchers.IO) {
                    api.stockAdd(stockTakeId = id, barcodeOrSku = code, qty = 1)
                }
                val updated = updatedNet.toViewModel()
                // Update current-product card with Expected/Counted
                renderCurrent(updated)
                // Merge into list (add or replace)
                upsertRow(updated)
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val friendly =
                    if (msg.contains("52012", true) || msg.contains("No product", true))
                        "Unknown barcode/SKU"
                    else "Failed to add count"
                Toast.makeText(this@StockTakingActivity, friendly, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Add by tapping a row (+1 via its SKU)
    private fun addCountBySku(sku: String) {
        addCountByBarcode(sku)
    }

    private fun undoLast() {
        val id = stockTakeId ?: return
        lifecycleScope.launch {
            try {
                val updatedNet: NetStockItem = withContext(Dispatchers.IO) {
                    api.stockUndoLast(stockTakeId = id)
                }
                val updated = updatedNet.toViewModel()
                renderCurrent(updated)   // show the item that was actually affected
                upsertRow(updated)
            } catch (_: Exception) {
                Toast.makeText(this@StockTakingActivity, "Nothing to undo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /* ---------------------------- UI helpers ---------------------------- */

    private fun renderCurrent(item: StockItem) {
        txtProductTitle?.text = "${item.name} (${item.sku})"
        txtExpected?.text = "Expected: ${item.expected}"
        txtCounted?.text = "Counted: ${item.counted}"
        val pct = if (item.expected <= 0) 0 else (item.counted * 100 / item.expected).coerceIn(0, 100)
        progressCount?.max = 100
        progressCount?.setProgress(pct, true)
    }

    private fun upsertRow(updated: StockItem) {
        val idxAll = allItems.indexOfFirst { it.sku == updated.sku }
        if (idxAll >= 0) allItems[idxAll] = updated else allItems.add(updated)

        val idxVis = visibleItems.indexOfFirst { it.sku == updated.sku }
        if (idxVis >= 0) {
            visibleItems[idxVis] = updated
            adapter?.notifyItemChanged(idxVis)
        } else {
            // If filtered out, refresh list based on current query
            filterList(edtSearch?.text?.toString().orEmpty())
        }
    }

    /* ---------------------------- Your existing helpers ---------------------------- */

    private fun seedDemoData() {
        allItems.clear()
        allItems += listOf(
            StockItem(name = "HDMI Cable 2m", sku = "SKU-1001", expected = 12),
            StockItem(name = "USB-C Charger 65W", sku = "SKU-1002", expected = 8),
            StockItem(name = "Wireless Mouse", sku = "SKU-1003", expected = 15),
            StockItem(name = "Keyboard (Mechanical)", sku = "SKU-1004", expected = 10),
            StockItem(name = "RJ45 Patch 5m", sku = "SKU-1005", expected = 20),
            StockItem(name = "External HDD 1TB", sku = "SKU-1006", expected = 6)
        )
        visibleItems.clear()
        visibleItems += allItems.map { it.copy() }
        adapter?.notifyDataSetChanged()
    }

    private fun filterList(query: String) {
        visibleItems.clear()
        if (query.isBlank()) {
            visibleItems += allItems
        } else {
            val q = query.trim().lowercase()
            visibleItems += allItems.filter { it.name.lowercase().contains(q) || it.sku.lowercase().contains(q) }
        }
        adapter?.notifyDataSetChanged()
    }

    // Options menu (unchanged)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_global, menu)
        val white = ContextCompat.getColor(this, R.color.white)
        for (i in 0 until menu.size()) menu.getItem(i).icon?.setTint(white)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_back -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
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

    /* ---------------------------- Mapping ---------------------------- */

    // Map backend row (za.tws.scan.net.StockItem) -> your UI model (local StockItem)
    private fun NetStockItem.toViewModel(): StockItem =
        StockItem(
            name = this.Name ?: "(no name)",
            sku = this.Sku ?: "(no sku)",
            expected = this.ExpectedQty ?: 0,
            counted = this.CountedQty ?: 0
        )
}

/* ---------------------------- Your existing UI model & adapter ---------------------------- */

private data class StockItem(
    val name: String,
    val sku: String,
    val expected: Int,
    var counted: Int = 0
)

private enum class RowAction { Increment, Decrement }

private class StockAdapter(
    private val items: List<StockItem>,
    private val onAction: (StockItem, RowAction) -> Unit
) : RecyclerView.Adapter<StockAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtSku: TextView = view.findViewById(R.id.txtSku)
        val txtExpected: TextView = view.findViewById(R.id.txtExpectedRow)
        val txtCounted: TextView = view.findViewById(R.id.txtCountedRow)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progressRow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_stock_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.txtName.text = item.name
        holder.txtSku.text = item.sku
        holder.txtExpected.text = "Expected: ${item.expected}"
        holder.txtCounted.text = "Counted: ${item.counted}"

        val pct = if (item.expected <= 0) 0 else (item.counted * 100 / item.expected).coerceIn(0, 100)
        holder.progress.max = 100
        holder.progress.setProgress(pct, true)

        holder.itemView.setOnClickListener { onAction(item, RowAction.Increment) }
        holder.itemView.setOnLongClickListener {
            onAction(item, RowAction.Decrement)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}