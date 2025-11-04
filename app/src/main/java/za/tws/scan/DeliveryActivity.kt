package za.tws.scan

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.tws.scan.net.*

class DeliveryActivity : AppCompatActivity() {

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar

    // Manual entry + round scan
    private lateinit var tilPackageEntry: TextInputLayout
    private lateinit var edtPackageNumber: TextInputEditText
    private lateinit var btnScanPackage: MaterialButton

    // Summary strip
    private lateinit var txtTotal: TextView
    private lateinit var txtToLoad: TextView
    private lateinit var txtLoaded: TextView

    // Filters
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipAll: Chip
    private lateinit var chipToLoad: Chip
    private lateinit var chipLoaded: Chip
    private lateinit var chipDelivered: Chip

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var fabScan: ExtendedFloatingActionButton

    // API
    private lateinit var api: ApiService

    // Data & adapter
    private val packages = mutableListOf<DeliveryPackageNet>()
    private lateinit var adapter: DeliveryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_delivery)

        // Toolbar/AppBar
        toolbar = findViewById(R.id.toolbar)
        appBar = findViewById(R.id.appbar)
        setSupportActionBar(toolbar)
        window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, sb.top, view.paddingRight, view.paddingBottom)
            insets
        }

        // API
        api = ApiClient.create { null }

        // Bind views
        tilPackageEntry = findViewById(R.id.tilPackageEntry)
        edtPackageNumber = findViewById(R.id.edtPackageNumber)
        btnScanPackage = findViewById(R.id.btnScanPackage)

        txtTotal = findViewById(R.id.txtTotal)
        txtToLoad = findViewById(R.id.txtToLoad)
        txtLoaded = findViewById(R.id.txtLoaded)

        chipGroup = findViewById(R.id.chipGroupFilters)
        chipAll = findViewById(R.id.chipAll)
        chipToLoad = findViewById(R.id.chipToLoad)
        chipLoaded = findViewById(R.id.chipLoaded)
        chipDelivered = findViewById(R.id.chipDelivered)

        recycler = findViewById(R.id.recyclerPackages)
        emptyView = findViewById(R.id.emptyView)
        fabScan = findViewById(R.id.fabScan)

        // Recycler
        adapter = DeliveryAdapter(
            items = packages,
            onClick = { pkg ->
                // Show details bottom sheet
                lifecycleScope.launch {
                    try {
                        val details = withContext(Dispatchers.IO) {
                            api.deliveryGetPackage(pkg.PackageNumber ?: return@withContext pkg)
                        }
                        PackageDetailsBottomSheet.newInstance(
                            pkgId = details.PackageNumber ?: "",
                            destination = details.Destination ?: "(no destination)",
                            contact = details.Driver ?: "",
                            notes = "",
                            status = details.Status ?: ""
                        ).show(supportFragmentManager, "pkg_details")
                    } catch (_: Exception) {
                        Toast.makeText(this@DeliveryActivity, "Couldn’t load package details", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onLongClick = { pkg ->
                // Long-press: if Loaded → mark Delivered
                val number = pkg.PackageNumber.orEmpty()
                if (number.isBlank()) return@DeliveryAdapter
                if ((pkg.Status ?: "") == "Loaded") {
                    confirmAndDeliver(number)
                } else {
                    Toast.makeText(this, "Hold to deliver only when status is Loaded", Toast.LENGTH_SHORT).show()
                }
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        ViewCompat.setOnApplyWindowInsetsListener(recycler) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sb.bottom + dp(72))
            insets
        }

        // Manual entry → process (smart: load vs deliver)
        tilPackageEntry.isEndIconVisible = false
        tilPackageEntry.setEndIconOnClickListener { edtPackageNumber.setText("") }
        edtPackageNumber.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val pkg = v.text?.toString()?.trim().orEmpty()
                if (pkg.isNotBlank()) processNumber(pkg)
                true
            } else false
        }

        // Round scan button → process
        btnScanPackage.setOnClickListener {
            val typed = edtPackageNumber.text?.toString()?.trim().orEmpty()
            if (typed.isNotBlank()) processNumber(typed)
        }

        // Filter chips → reload list
        chipAll.isChecked = true
        chipGroup.setOnCheckedStateChangeListener { _, ids ->
            val status = when {
                ids.contains(R.id.chipToLoad)   -> "To Load"
                ids.contains(R.id.chipLoaded)    -> "Loaded"
                ids.contains(R.id.chipDelivered) -> "Delivered"
                else -> null
            }
            toolbar.subtitle = if (status == null) "Filter: All" else "Filter: $status"
            loadPackages(status = status)
        }

        // FAB: process whatever is typed/scanned
        fabScan.setOnClickListener {
            val typed = edtPackageNumber.text?.toString()?.trim().orEmpty()
            if (typed.isBlank()) {
                Toast.makeText(this, "Enter/scan a package number first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processNumber(typed)
        }
    }

    override fun onResume() {
        super.onResume()
        loadPackages(status = null) // initial
    }

    /* -------------------- Core actions -------------------- */

    /** Smart handler: checks current status, then calls the correct endpoint. */
    private fun processNumber(packageNumber: String) {
        lifecycleScope.launch {
            try {
                val current = withContext(Dispatchers.IO) {
                    api.deliveryGetPackage(packageNumber)
                }
                val status = current.Status ?: "To Load"
                if (status == "Loaded") {
                    markDelivered(packageNumber)
                } else {
                    markLoaded(packageNumber)
                }
            } catch (_: Exception) {
                // if lookup fails, fall back to markLoaded first
                markLoaded(packageNumber)
            }
        }
    }

    private fun markLoaded(packageNumber: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Alternatively: api.deliveryScanToLoad(ScanToLoadRequest(packageNumber))
                    api.deliveryMarkLoaded(packageNumber)
                }
                Toast.makeText(this@DeliveryActivity, "Marked \"$packageNumber\" as Loaded", Toast.LENGTH_SHORT).show()
                refreshAfterMutation()
            } catch (_: Exception) {
                Toast.makeText(this@DeliveryActivity, "Couldn’t mark as Loaded", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markDelivered(packageNumber: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.deliveryMarkDelivered(packageNumber)
                }
                Toast.makeText(this@DeliveryActivity, "Delivered \"$packageNumber\" ✅", Toast.LENGTH_SHORT).show()
                refreshAfterMutation()
            } catch (_: Exception) {
                Toast.makeText(this@DeliveryActivity, "Couldn’t mark as Delivered", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmAndDeliver(packageNumber: String) {
        // Keep it simple (no dialog lib): deliver immediately; swap to a dialog if you want confirmation.
        markDelivered(packageNumber)
    }

    private fun refreshAfterMutation() {
        // Keep current filter and clear entry
        val status = currentStatusFilter()
        loadPackages(status = status)
        edtPackageNumber.setText("")
    }

    /* -------------------- Networking -------------------- */

    private fun loadPackages(search: String? = null, status: String? = null) {
        lifecycleScope.launch {
            try {
                val resp: DeliveryListResponse = withContext(Dispatchers.IO) {
                    api.deliveryList(search = search, status = status, top = 100)
                }
                packages.clear()
                packages += resp.items
                adapter.notifyDataSetChanged()

                txtTotal.text = (resp.counts.Total ?: 0).toString()
                txtToLoad.text = (resp.counts.ToLoad ?: 0).toString()
                txtLoaded.text = (resp.counts.Loaded ?: 0).toString()

                emptyView.visibility = if (packages.isEmpty()) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                Toast.makeText(this@DeliveryActivity, "Failed to load packages", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun currentStatusFilter(): String? {
        return when {
            chipToLoad.isChecked    -> "To Load"
            chipLoaded.isChecked     -> "Loaded"
            chipDelivered.isChecked  -> "Delivered"
            else -> null
        }
    }

    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()

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
                startActivity(intent); finish(); true
            }
            R.id.action_menu -> {
                Toast.makeText(this, "Menu clicked", Toast.LENGTH_SHORT).show(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

/** Adapter now supports long-press -> deliver when Loaded */
private class DeliveryAdapter(
    private val items: List<DeliveryPackageNet>,
    private val onClick: (DeliveryPackageNet) -> Unit,
    private val onLongClick: (DeliveryPackageNet) -> Unit
) : RecyclerView.Adapter<DeliveryAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val subtitle: TextView = view.findViewById(android.R.id.text2)
        init {
            view.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) onClick(items[bindingAdapterPosition])
            }
            view.setOnLongClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onLongClick(items[bindingAdapterPosition])
                    return@setOnLongClickListener true
                }
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.title.text = it.PackageNumber ?: "(no number)"
        val dest = it.Destination ?: "(no destination)"
        val status = it.Status ?: ""
        holder.subtitle.text = "Destination: $dest • Status: $status"
    }
}