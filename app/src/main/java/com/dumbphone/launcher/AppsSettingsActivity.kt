package com.dumbphone.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class AppsSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var appListRecycler: RecyclerView
    private lateinit var gestureDetector: GestureDetector

    private var allApps: List<ResolveInfo> = emptyList()
    private var filteredApps: List<ResolveInfo> = emptyList()
    private lateinit var appAdapter: AppToggleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps_settings)

        prefs = PrefsManager(this)

        @Suppress("DEPRECATION")
        window.statusBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.BLACK

        // Swipe-down to go back
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val dy = e2.y - (e1?.y ?: 0f)
                if (dy > 100 && abs(velocityY) > 200) {
                    finish()
                    return true
                }
                return false
            }
        })

        appListRecycler = findViewById(R.id.appListRecycler)
        appListRecycler.layoutManager = LinearLayoutManager(this)

        // Search
        val searchInput = findViewById<EditText>(R.id.appSearchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString() ?: "")
            }
        })

        loadAllApps()
        applyTheme()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun loadAllApps() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        allApps = pm.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        appAdapter = AppToggleAdapter(pm)
        appListRecycler.adapter = appAdapter
        appAdapter.submitList(allApps)
    }

    private fun filterApps(query: String) {
        val pm = packageManager
        filteredApps = if (query.isBlank()) {
            allApps
        } else {
            val q = query.lowercase()
            allApps.filter {
                it.loadLabel(pm).toString().lowercase().contains(q) ||
                it.activityInfo.packageName.lowercase().contains(q)
            }
        }
        appAdapter.submitList(filteredApps)
    }

    private fun applyTheme() {
        val fgColor = prefs.getFgColour()

        findViewById<View>(R.id.appsSettingsRoot).setBackgroundColor(Color.BLACK)
        findViewById<TextView>(R.id.appsTitle).setTextColor(fgColor)
    }

    // ── App Toggle Adapter ──────────────────────────────────────────────

    inner class AppToggleAdapter(
        private val pm: PackageManager
    ) : ListAdapter<ResolveInfo, AppToggleAdapter.ViewHolder>(AppDiffCallback()) {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.toggleAppIcon)
            val label: TextView = view.findViewById(R.id.toggleAppLabel)
            val packageText: TextView = view.findViewById(R.id.toggleAppPackage)
            val toggle: CheckBox = view.findViewById(R.id.toggleAppCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_toggle, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = getItem(position)
            val pkg = app.activityInfo.packageName
            val label = app.loadLabel(pm).toString()

            holder.icon.setImageDrawable(app.loadIcon(pm))
            holder.label.text = label
            holder.packageText.text = pkg

            // Avoid triggering the listener when setting checked state
            holder.toggle.setOnCheckedChangeListener(null)
            holder.toggle.isChecked = prefs.isAppWhitelisted(pkg)

            holder.label.setTextColor(prefs.getFgColour())
            holder.packageText.setTextColor(Color.parseColor("#555555"))

            holder.toggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    prefs.addWhitelistedApp(pkg)
                } else {
                    prefs.removeWhitelistedApp(pkg)
                }
            }

            holder.itemView.setOnClickListener {
                holder.toggle.isChecked = !holder.toggle.isChecked
            }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<ResolveInfo>() {
        override fun areItemsTheSame(oldItem: ResolveInfo, newItem: ResolveInfo): Boolean =
            oldItem.activityInfo.packageName == newItem.activityInfo.packageName

        override fun areContentsTheSame(oldItem: ResolveInfo, newItem: ResolveInfo): Boolean =
            oldItem.activityInfo.packageName == newItem.activityInfo.packageName
    }
}
