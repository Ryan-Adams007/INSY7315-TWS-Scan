package za.tws.scan

import android.content.Intent
import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class PackingActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep content below system bars (no overlap)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_packing)

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

        // Bind views
        tilItemSerial = findViewById(R.id.tilItemSerial)
        edtItemSerial = findViewById(R.id.edtItemSerial)
        txtQty = findViewById(R.id.txtQty)
        chipPackageId = findViewById(R.id.chipPackageId)
        btnAddToPackage = findViewById(R.id.btnAddToPackage)
        btnUndo = findViewById(R.id.btnUndo)
        btnStartNewPackage = findViewById(R.id.btnStartNewPackage)
        btnClearPackage = findViewById(R.id.btnClearPackage)
        fabSealAndPrint = findViewById(R.id.fabSealAndPrint)
        recyclerPackageItems = findViewById(R.id.recyclerPackageItems)

        // Quantity buttons are custom includes; fetch inner MaterialButton by id from each include
        val minusInclude: View = findViewById(R.id.btnQtyMinus)
        val plusInclude: View = findViewById(R.id.btnQtyPlus)
        val minusBtn: MaterialButton = minusInclude.findViewById(R.id.roundMinusButton)
        val plusBtn: MaterialButton = plusInclude.findViewById(R.id.roundPlusButton)

        // === Requested snippet (wired into your flow) ===
        val scanBtn: MaterialButton = findViewById(R.id.btnScanPackage)
        btnScanPackage = scanBtn // keep your field in sync, if you use it elsewhere
        scanBtn.setOnClickListener {
            // open scanner
            Toast.makeText(this, "Scan a package barcode…", Toast.LENGTH_SHORT).show()
            // demo: set a fake package id to show the flow
            chipPackageId.text = "PKG-123456"
        }
        // ===============================================

        // Qty logic
        minusBtn.setOnClickListener {
            val current = txtQty.text.toString().toIntOrNull() ?: 1
            val next = (current - 1).coerceAtLeast(1)
            txtQty.text = next.toString()
        }
        plusBtn.setOnClickListener {
            val current = txtQty.text.toString().toIntOrNull() ?: 1
            val next = (current + 1).coerceAtMost(9999)
            txtQty.text = next.toString()
        }

        // Item scan end icon (camera)
        tilItemSerial.setEndIconOnClickListener {
            Toast.makeText(this, "Open scanner for item serial…", Toast.LENGTH_SHORT).show()
        }

        // Add to package
        btnAddToPackage.setOnClickListener {
            val serial = edtItemSerial.text?.toString().orEmpty().trim()
            if (serial.isEmpty()) {
                tilItemSerial.error = "Enter or scan a serial"
                return@setOnClickListener
            } else {
                tilItemSerial.error = null
            }
            val qty = txtQty.text.toString().toIntOrNull() ?: 1
            Toast.makeText(this, "Added $serial x$qty to package", Toast.LENGTH_SHORT).show()
            edtItemSerial.setText("")
        }

        // Undo last add
        btnUndo.setOnClickListener {
            Toast.makeText(this, "Last action undone", Toast.LENGTH_SHORT).show()
        }

        // Start new package
        btnStartNewPackage.setOnClickListener {
            chipPackageId.text = "No package set"
            Toast.makeText(this, "Started a new package", Toast.LENGTH_SHORT).show()
        }

        // Clear package
        btnClearPackage.setOnClickListener {
            Toast.makeText(this, "Cleared items from package", Toast.LENGTH_SHORT).show()
        }

        // Seal & Print
        fabSealAndPrint.setOnClickListener {
            Toast.makeText(this, "Package sealed. Sending label to printer…", Toast.LENGTH_SHORT).show()
        }

        // Recycler placeholder setup
        recyclerPackageItems.layoutManager = LinearLayoutManager(this)
        recyclerPackageItems.adapter = object : RecyclerView.Adapter<SimpleVH>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SimpleVH {
                val tv = TextView(parent.context).apply {
                    setPadding(24, 24, 24, 24)
                    text = "No items yet"
                    setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
                }
                return SimpleVH(tv)
            }
            override fun getItemCount(): Int = 1
            override fun onBindViewHolder(holder: SimpleVH, position: Int) {}
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

    private class SimpleVH(view: View) : RecyclerView.ViewHolder(view)
}