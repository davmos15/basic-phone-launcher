package com.dumbphone.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var titleText: TextView
    private lateinit var rootLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)

        prefs = PrefsManager(this)

        @Suppress("DEPRECATION")
        window.statusBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.BLACK

        rootLayout = findViewById(R.id.drawerRootLayout)
        titleText = findViewById(R.id.drawerTitle)
        recyclerView = findViewById(R.id.appGrid)

        // Nokia-style 3-column grid
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        loadApps()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        applyTheme()
    }

    private fun loadApps() {
        val whitelisted = prefs.whitelistedApps
        val pm = packageManager

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val allApps = pm.queryIntentActivities(mainIntent, 0)

        val filteredApps = allApps.filter { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            whitelisted.contains(pkg) && pkg != packageName
        }.sortedBy { it.loadLabel(pm).toString().lowercase() }

        recyclerView.adapter = AppGridAdapter(filteredApps, pm)

        titleText.text = if (filteredApps.isEmpty()) "MENU (empty)" else "MENU"
    }

    private fun applyTheme() {
        val fgColor = prefs.clockColour
        rootLayout.setBackgroundColor(Color.BLACK)
        titleText.setTextColor(fgColor)
    }

    // --- RecyclerView Adapter ---
    inner class AppGridAdapter(
        private val apps: List<ResolveInfo>,
        private val pm: PackageManager
    ) : RecyclerView.Adapter<AppGridAdapter.AppViewHolder>() {

        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val label: TextView = view.findViewById(R.id.appLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = apps[position]
            val label = app.loadLabel(pm).toString()
            val icon = app.loadIcon(pm)

            holder.label.text = if (label.length > 10) label.substring(0, 9) + "\u2026" else label
            holder.icon.setImageDrawable(icon)

            // Monochrome icons with chosen colour
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            holder.icon.colorFilter = ColorMatrixColorFilter(matrix)
            holder.icon.alpha = 0.8f
            holder.label.setTextColor(prefs.clockColour)

            holder.itemView.setOnClickListener {
                val launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this@AppDrawerActivity, "Cannot open $label", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount() = apps.size
    }
}
