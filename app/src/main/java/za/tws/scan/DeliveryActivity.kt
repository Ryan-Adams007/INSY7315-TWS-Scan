package za.tws.scan

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

class DeliveryActivity : AppCompatActivity() {

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar

    private lateinit var tilSearch: TextInputLayout
    private lateinit var edtSearch: TextInputEditText
    private lateinit var scanRoundBtn: MaterialButton

    private lateinit var chipGroup: ChipGroup
    private lateinit var chipAll: Chip
    private lateinit var chipToLoad: Chip
    private lateinit var chipLoaded: Chip

    private lateinit var recycler: RecyclerView
    private lateinit var fabQuickScan: ExtendedFloatingActionButton

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

        // Bind
        tilSearch = findViewById(R.id.tilSearch)
        edtSearch = findViewById(R.id.edtSearch)
        scanRoundBtn = findViewById(R.id.btnScanPackageSearch)

        chipGroup = findViewById(R.id.chipGroupFilters)
        chipAll = findViewById(R.id.chipAll)
        chipToLoad = findViewById(R.id.chipToLoad)
        chipLoaded = findViewById(R.id.chipLoaded)

        recycler = findViewById(R.id.recyclerPackages)
        fabQuickScan = findViewById(R.id.fabQuickScan)

        // Search polish
        tilSearch.isEndIconVisible = false
        tilSearch.setEndIconOnClickListener { edtSearch.setText("") }
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilSearch.isEndIconVisible = !s.isNullOrBlank()
            }
        })
        edtSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                Toast.makeText(this, "Searching \"${v.text}\"…", Toast.LENGTH_SHORT).show()
                true
            } else false
        }
        scanRoundBtn.setOnClickListener {
            Toast.makeText(this, "Open camera to scan package…", Toast.LENGTH_SHORT).show()
        }

        // Chips simple styling (colors already defined in colors.xml)
        fun applyChip(checked: Boolean, chip: Chip, bg: Int, stroke: Int, text: Int) {
            chip.setChipBackgroundColorResource(if (checked) stroke else bg)
            chip.setChipStrokeColorResource(stroke)
            chip.chipStrokeWidth = if (checked) 0f else 1f
            chip.setTextColor(
                if (checked) ContextCompat.getColor(this, R.color.white)
                else ContextCompat.getColor(this, text)
            )
        }
        fun restyleAll() {
            applyChip(chipAll.isChecked, chipAll, R.color.chipNeutralBackground, R.color.chipNeutralBorder, R.color.chipNeutralText)
            applyChip(chipToLoad.isChecked, chipToLoad, R.color.chipAmberBackground, R.color.chipAmberBorder, R.color.chipAmberText)
            applyChip(chipLoaded.isChecked, chipLoaded, R.color.chipGreenBackground, R.color.chipGreenBorder, R.color.chipGreenText)
        }
        chipAll.isChecked = true
        listOf(chipAll, chipToLoad, chipLoaded).forEach { c ->
            c.setOnCheckedChangeListener { _, _ -> restyleAll() }
        }
        chipGroup.setOnCheckedStateChangeListener { _, ids ->
            val selected = when {
                ids.contains(R.id.chipToLoad) -> "To Load"
                ids.contains(R.id.chipLoaded) -> "Loaded"
                else -> "All"
            }
            toolbar.subtitle = "Filter: $selected"
            Toast.makeText(this, "Filter set: $selected", Toast.LENGTH_SHORT).show()
        }
        restyleAll()

        // Recycler + open bottom sheet on click
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = object : RecyclerView.Adapter<SimpleVH>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SimpleVH {
                val v = layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
                return SimpleVH(v)
            }
            override fun getItemCount(): Int = 8
            override fun onBindViewHolder(holder: SimpleVH, position: Int) {
                val title = holder.itemView.findViewById<android.widget.TextView>(android.R.id.text1)
                val sub = holder.itemView.findViewById<android.widget.TextView>(android.R.id.text2)
                val pkgId = "PKG-10${position}23"
                title.text = pkgId
                sub.text = "Destination: Warehouse B • Status: To Load"

                holder.itemView.setOnClickListener {
                    // 🔽 Open the modal bottom sheet with sample data
                    PackageDetailsBottomSheet.newInstance(
                        pkgId = pkgId,
                        destination = "Warehouse B",
                        contact = "+27 82 000 000$position",
                        notes = "Handle with care",
                        status = if (position % 2 == 0) "To Load" else "Loaded"
                    ).show(supportFragmentManager, "pkg_details")
                }
            }
        }
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        ViewCompat.setOnApplyWindowInsetsListener(recycler) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sb.bottom + dp(72))
            insets
        }

        // FAB behavior
        fabQuickScan.setOnClickListener {
            Toast.makeText(this, "Quick scan to mark package loaded…", Toast.LENGTH_SHORT).show()
        }
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 6 && fabQuickScan.isShown) fabQuickScan.shrink()
                else if (dy < -6 && !fabQuickScan.isExtended) fabQuickScan.extend()
            }
        })
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

    private class SimpleVH(view: View) : RecyclerView.ViewHolder(view)
}