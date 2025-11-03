package za.tws.scan

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.HapticFeedbackConstants
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class PickingActivity : AppCompatActivity() {

    private lateinit var tilSerial: TextInputLayout
    private lateinit var edtSerial: TextInputEditText
    private lateinit var txtQty: TextView
    private lateinit var btnMinus: MaterialButton
    private lateinit var btnPlus: MaterialButton
    private lateinit var btnAdd: MaterialButton
    private lateinit var fabDone: ExtendedFloatingActionButton

    private var qty = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep content below system bars (no overlap)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_picking)

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

        // --- View refs ---
        tilSerial = findViewById(R.id.tilSerial)
        edtSerial = findViewById(R.id.edtSerial)
        txtQty = findViewById(R.id.txtQty)

        // Round include buttons expose inner IDs:
        btnMinus = findViewById(R.id.roundMinusButton)
        btnPlus  = findViewById(R.id.roundPlusButton)

        btnAdd = findViewById(R.id.btnScanConfirm)
        fabDone = findViewById(R.id.fabCompletePick)

        // Initial qty
        qty = txtQty.text.toString().toIntOrNull() ?: 1
        txtQty.text = qty.toString()

        // --- Tiny “bounce” tap effect helper + haptic ---
        fun tap(v: View, block: () -> Unit) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            v.animate()
                .scaleX(0.94f).scaleY(0.94f).setDuration(70)
                .withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    block()
                }.start()
        }

        // Minus
        btnMinus.setOnClickListener { v ->
            tap(v) {
                if (qty > 1) qty--
                txtQty.text = qty.toString()
            }
        }

        // Plus
        btnPlus.setOnClickListener { v ->
            tap(v) {
                qty++
                txtQty.text = qty.toString()
            }
        }

        // End-icon (scanner)
        tilSerial.setEndIconOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(this, "Open scanner…", Toast.LENGTH_SHORT).show()
            // TODO: launch your scanner and set result into edtSerial.setText(scanned)
        }

        // Add to Pick
        btnAdd.setOnClickListener { v ->
            tap(v) {
                val serial = edtSerial.text?.toString()?.trim().orEmpty()
                if (serial.isEmpty()) {
                    tilSerial.error = "Serial required"
                    return@tap
                }
                tilSerial.error = null
                // TODO: add row to your list/adapter if needed
                Toast.makeText(this, "Added: $serial ×$qty", Toast.LENGTH_SHORT).show()
                edtSerial.setText("")
                qty = 1
                txtQty.text = "1"
            }
        }

        // Complete Pick (FAB)
        fabDone.setOnClickListener { v ->
            tap(v) {
                // TODO: finalize pick, sync, etc.
                Toast.makeText(this, "Pick completed ✅", Toast.LENGTH_SHORT).show()
                // Example: return to main
                // startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                // finish()
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
                // Navigate back to MainActivity
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