package za.tws.scan

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep content below system bars (no overlap)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContentView(R.layout.activity_main)

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

        // Card click listeners
        findViewById<MaterialCardView>(R.id.cardPicking).setOnClickListener {
            startActivity(Intent(this, PickingActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardPacking).setOnClickListener {
            startActivity(Intent(this, PackingActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardDelivery).setOnClickListener {
            startActivity(Intent(this, DeliveryActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardStock).setOnClickListener {
            startActivity(Intent(this, StockTakingActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val white = ContextCompat.getColor(this, R.color.white)
        for (i in 0 until menu.size()) menu.getItem(i).icon?.setTint(white)
        return true
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}