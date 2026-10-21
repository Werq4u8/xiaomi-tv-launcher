package com.tvlauncher.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var tvFeaturedLabel: TextView
    private lateinit var tvWifiName: TextView
    private lateinit var rvApps: RecyclerView
    private lateinit var rvAllApps: RecyclerView
    private lateinit var btnSettings: View
    private lateinit var btnInput: View
    private lateinit var btnWifi: View

    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var clockRunnable: Runnable

    // Список "избранных" — первая строка (можно редактировать)
    private val favoritePackages = listOf(
        "com.netflix.ninja",
        "com.amazon.avod.thirdpartyclient",
        "com.google.android.youtube.tv",
        "com.jio.jioplay.tv",
        "com.disney.disneyplus",
        "com.hbo.hbonow"
    )

    // Receiver для обновления списка приложений при установке/удалении
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupButtons()
        startClock()
        loadApps()
        loadWifiInfo()

        // Слушаем изменения в списке пакетов
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    private fun initViews() {
        tvTime = findViewById(R.id.tv_time)
        tvDate = findViewById(R.id.tv_date)
        tvGreeting = findViewById(R.id.tv_greeting)
        tvFeaturedLabel = findViewById(R.id.tv_featured_label)
        tvWifiName = findViewById(R.id.tv_wifi_name)
        rvApps = findViewById(R.id.rv_apps)
        rvAllApps = findViewById(R.id.rv_all_apps)
        btnSettings = findViewById(R.id.btn_settings)
        btnInput = findViewById(R.id.btn_input)
        btnWifi = findViewById(R.id.btn_wifi)

        // Горизонтальный скролл
        rvApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAllApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // Убираем мерцание при скролле
        rvApps.overScrollMode = View.OVER_SCROLL_NEVER
        rvAllApps.overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun setupButtons() {
        btnSettings.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }

        btnInput.setOnClickListener {
            // Переключение источника входа (HDMI и т.д.)
            val intent = Intent("android.intent.action.MAIN").apply {
                addCategory("android.intent.category.LAUNCHER")
                setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { startActivity(intent) }
        }

        btnWifi.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun startClock() {
        val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
        val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH)

        clockRunnable = object : Runnable {
            override fun run() {
                val now = Date()
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)

                // Время с AM/PM
                tvTime.text = "${timeFormat.format(now)} ${amPmFormat.format(now).uppercase()}"

                // Дата
                tvDate.text = dateFormat.format(now).uppercase()

                // Приветствие в зависимости от времени суток
                tvGreeting.text = when {
                    hour < 12 -> getString(R.string.greeting_morning)
                    hour < 17 -> getString(R.string.greeting_afternoon)
                    hour < 21 -> getString(R.string.greeting_evening)
                    else -> getString(R.string.greeting_night)
                }

                clockHandler.postDelayed(this, 1000)
            }
        }
        clockHandler.post(clockRunnable)
    }

    private fun loadApps() {
        val pm = packageManager

        // --- Строка избранных (верхняя) ---
        val favoriteApps = favoritePackages.mapNotNull { pkg ->
            runCatching {
                AppInfo(
                    label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(),
                    packageName = pkg,
                    icon = pm.getApplicationIcon(pkg)
                )
            }.getOrNull()
        }

        // --- Все TV-приложения (нижняя строка) ---
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val allApps = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
            .map { ri ->
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    icon = ri.loadIcon(pm)
                )
            }
            .filter { it.packageName != packageName } // убираем себя
            .sortedBy { it.label.lowercase() }

        // Устанавливаем адаптеры
        val favAdapter = AppAdapter(favoriteApps) { app ->
            launchApp(app.packageName)
        }
        favAdapter.setOnFocusChangeListener { appName ->
            tvFeaturedLabel.text = appName
        }

        val allAdapter = AppAdapter(allApps) { app ->
            launchApp(app.packageName)
        }
        allAdapter.setOnFocusChangeListener { appName ->
            tvFeaturedLabel.text = appName
        }

        rvApps.adapter = favAdapter
        rvAllApps.adapter = allAdapter

        // Показываем название первого приложения
        if (favoriteApps.isNotEmpty()) {
            tvFeaturedLabel.text = favoriteApps.first().label
        } else if (allApps.isNotEmpty()) {
            tvFeaturedLabel.text = allApps.first().label
        }
    }

    private fun loadWifiInfo() {
        runCatching {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ssid = wifiManager.connectionInfo.ssid
                ?.trim('"')
                ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                ?: "Wi-Fi"
            tvWifiName.text = "▲  $ssid"
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
    }

    // Блокируем кнопку Back чтобы не выйти из лаунчера
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        runCatching { unregisterReceiver(packageReceiver) }
    }
}
