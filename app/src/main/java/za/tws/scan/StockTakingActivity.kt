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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class StockTakingActivity : AppCompatActivity() {

    // --- Views that might exist in your activity_stock_taking.xml (all null-safe lookups) ---
    private var recycler: RecyclerView? = null
    private var scanBtn: MaterialButton? = null
    private var tilSearch: TextInputLayout? = null
    private var edtSearch: TextInputEditText? = null

    // --- Data ---
    private val allItems = mutableListOf<StockItem>()
    private val visibleItems = mutableListOf<StockItem>()
    private var adapter: StockAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep content below system bars (no overlap)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_stock_taking)

        // Toolbar/AppBar
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val appBar: AppBarLayout = findViewById(R.id.appbar)
        setSupportActionBar(toolbar)

        // Status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)

        // Apply top inset to AppBar only
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, view.paddingBottom)
            insets
        }

        // --- Bind optional views if they exist in your layout ---
        recycler = findViewById(R.id.recyclerStock)
        scanBtn = findViewById(R.id.btnScan)
        tilSearch = findViewById(R.id.tilSearch)
        edtSearch = findViewById(R.id.edtSearch)

        // --- Seed demo data ---
        seedDemoData()

        // --- Setup Recycler (if present) ---
        recycler?.let { rv ->
            rv.layoutManager = LinearLayoutManager(this)
            adapter = StockAdapter(visibleItems) { item, action ->
                when (action) {
                    RowAction.Increment -> {
                        if (item.counted < item.expected) {
                            item.counted++
                            adapter?.notifyItemChanged(visibleItems.indexOf(item))
                        } else {
                            Toast.makeText(this, "Already at expected count", Toast.LENGTH_SHORT).show()
                        }
                    }
                    RowAction.Decrement -> {
                        if (item.counted > 0) {
                            item.counted--
                            adapter?.notifyItemChanged(visibleItems.indexOf(item))
                        }
                    }
                }
            }
            rv.adapter = adapter
        }

        // --- Scan button (optional) ---
        scanBtn?.setOnClickListener {
            Toast.makeText(this, "Open scanner to count an item...", Toast.LENGTH_SHORT).show()
            // Later: jump to camera; on result, find SKU and increment that row.
        }

        // --- Search polish (optional) ---
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
    }

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
        // Start counted at 0 for demo
        visibleItems.clear()
        visibleItems += allItems.map { it.copy() }
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

    // --- Options menu (global actions you already had) ---
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
}

/* ---------------------------- Models & Adapter ---------------------------- */

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
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock_row, parent, false)
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
        holder.progress.setProgress(pct, /*animate=*/true)

        // Tap to increment; long press to decrement
        holder.itemView.setOnClickListener { onAction(item, RowAction.Increment) }
        holder.itemView.setOnLongClickListener {
            onAction(item, RowAction.Decrement)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}