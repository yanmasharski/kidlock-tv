package uk.telegramgames.kidlock

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class ScreenTimeAccessibilityService : AccessibilityService() {

    private lateinit var dataRepository: DataRepository
    private lateinit var usageStatsHelper: UsageStatsHelper
    private val handler = Handler(Looper.getMainLooper())
    private var periodicCheckRunnable: Runnable? = null
    // Short interval so we react quickly to overuse on TV devices
    private val CHECK_INTERVAL_MS = 5_000L
    @Volatile
    private var lastKnownPackageName: String? = null
    @Volatile
    private var currentSessionStartTime: Long = 0L
    @Volatile
    private var lastBlockTime: Long = 0
    // Prevent rapid re-blocking loops when apps relaunch quickly
    private val MIN_BLOCK_INTERVAL_MS = 500L
    private val activityManager: ActivityManager by lazy {
        getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    private val usageStatsManager: UsageStatsManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        } else {
            null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        dataRepository = DataRepository.getInstance(this)
        usageStatsHelper = UsageStatsHelper(this)
        createNotificationChannel()

        getCurrentPackageName()?.let {
            if (!isSystemPackage(it) && it != packageName) {
                lastKnownPackageName = it
                currentSessionStartTime = System.currentTimeMillis()
            }
        }

        startPeriodicCheck()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        if (packageName == this.packageName) {
            lastKnownPackageName = packageName
            return
        }

        if (isSystemPackage(packageName)) {
            // Reset tracking when user is on launcher/home screen to avoid counting idle time
            lastKnownPackageName = null
            currentSessionStartTime = 0L
            return
        }

        if (packageName != lastKnownPackageName) {
            currentSessionStartTime = System.currentTimeMillis()
        }
        lastKnownPackageName = packageName

        if (!dataRepository.isBlockingEnabled()) {
            return
        }

        dataRepository.ensureDailyResetIfNeeded()
        val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
        val addedTime = dataRepository.getAddedTimeMinutes()
        val hasTime = usageStatsHelper.hasRemainingTime(dailyLimit, addedTime, lastKnownPackageName, currentSessionStartTime)

        if (!hasTime) {
            // Open KidLock instead of the target app once time is exhausted
            blockAppLaunch()
            // Ensure currently running app is closed if it already reached foreground
            forceCloseApp(packageName)
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android") ||
                packageName.startsWith("android") ||
                packageName == "com.google.android.tv.settings" ||
                packageName == "com.google.android.leanbacklauncher"
    }

    private fun blockAppLaunch() {
        // Защита от слишком частых блокировок
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < MIN_BLOCK_INTERVAL_MS) {
            return
        }
        lastBlockTime = now

        // Дополнительная проверка: убеждаемся, что мы не блокируем KidLock
        val currentPackage = getCurrentPackageName()
        if (currentPackage == this.packageName) {
            // Если текущее приложение - KidLock, не блокируем
            return
        }

        // Очищаем lastKnownPackageName, так как мы блокируем приложение и открываем KidLock
        lastKnownPackageName = null

        // Открываем само приложение KidLock вместо главного экрана
        val kidLockIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(kidLockIntent)

        // Показываем уведомление
        showBlockNotification()
    }

    private fun showBlockNotification() {
        val channelId = "screen_time_block"
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.time_limit_reached))
            .setContentText(getString(R.string.time_limit_reached_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_time_block",
                getString(R.string.time_limit_notifications),
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicCheck()
    }

    private fun startPeriodicCheck() {
        periodicCheckRunnable = object : Runnable {
            override fun run() {
                checkAndMinimizeApps()
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
        handler.post(periodicCheckRunnable!!)
    }

    private fun stopPeriodicCheck() {
        periodicCheckRunnable?.let {
            handler.removeCallbacks(it)
            periodicCheckRunnable = null
        }
    }

    private fun checkAndMinimizeApps() {
        if (!dataRepository.isBlockingEnabled()) {
            return
        }

        val currentPackageName = getCurrentPackageName()
        if (currentPackageName == null) {
            return
        }

        if (currentPackageName == this.packageName) {
            return
        }

        if (isSystemPackage(currentPackageName)) {
            // Reset tracking when user returns to launcher/home
            lastKnownPackageName = null
            currentSessionStartTime = 0L
            return
        }

        val now = System.currentTimeMillis()
        if (currentPackageName != lastKnownPackageName) {
            lastKnownPackageName = currentPackageName
            currentSessionStartTime = now
        }
        
        if (currentSessionStartTime > now || (now - currentSessionStartTime) > 24 * 60 * 60 * 1000L) {
            currentSessionStartTime = now
        }

        dataRepository.ensureDailyResetIfNeeded()
        val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
        val addedTime = dataRepository.getAddedTimeMinutes()
        
        val hasTime = usageStatsHelper.hasRemainingTime(dailyLimit, addedTime, lastKnownPackageName, currentSessionStartTime)

        if (hasTime) {
            return
        }

        forceCloseApp(currentPackageName)
        minimizeCurrentApp()
    }

    private fun getCurrentPackageName(): String? {
        return try {
            val fromAccessibility = rootInActiveWindow?.packageName?.toString()
            if (fromAccessibility != null && !isSystemPackage(fromAccessibility)) {
                return fromAccessibility
            }

            val fromUsageStats = getCurrentPackageFromUsageStats()
            if (fromUsageStats != null && !isSystemPackage(fromUsageStats)) {
                return fromUsageStats
            }

            val fromActivityManager = getCurrentPackageFromActivityManager()
            if (fromActivityManager != null && !isSystemPackage(fromActivityManager)) {
                return fromActivityManager
            }

            lastKnownPackageName
        } catch (e: Exception) {
            lastKnownPackageName
        }
    }

    private fun getCurrentPackageFromUsageStats(): String? {
        val manager = usageStatsManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null
        }

        return try {
            val time = System.currentTimeMillis()
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                time - 1000, // Последняя секунда
                time
            ) ?: return null

            // Находим приложение с наибольшим временем в foreground
            var mostRecent: UsageStats? = null
            for (usageStats in stats) {
                if (mostRecent == null || usageStats.lastTimeUsed > mostRecent.lastTimeUsed) {
                    mostRecent = usageStats
                }
            }

            mostRecent?.packageName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Получает текущее приложение через ActivityManager
     * Примечание: getRunningTasks требует специального разрешения и может не работать на новых версиях Android
     */
    private fun getCurrentPackageFromActivityManager(): String? {
        return try {
            // На Android 5.0+ getRunningTasks требует разрешения и может не работать
            // Используем только для старых версий Android
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                val runningTasks = activityManager.getRunningTasks(1)
                if (runningTasks.isNotEmpty()) {
                    return runningTasks[0].topActivity?.packageName
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Принудительно закрывает приложение
     * Примечание: killBackgroundProcesses работает только для фоновых процессов.
     * Для приложений в foreground используем возврат на домашний экран через minimizeCurrentApp()
     */
    private fun forceCloseApp(packageName: String) {
        try {
            // Пытаемся закрыть фоновые процессы приложения
            // Это может помочь, если приложение пытается остаться в памяти
            activityManager.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            // Игнорируем ошибки - приложение может быть уже закрыто или нет прав
            // Основное закрытие происходит через minimizeCurrentApp()
        }
    }

    /**
     * Сворачивает текущее приложение, открывая KidLock
     */
    private fun minimizeCurrentApp() {
        try {
            // Открываем само приложение KidLock вместо возврата на домашний экран
            val kidLockIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(kidLockIntent)
            // Очищаем lastKnownPackageName после открытия KidLock
            handler.postDelayed({
                lastKnownPackageName = null
            }, 500) // Небольшая задержка, чтобы убедиться, что KidLock открыт
        } catch (e: Exception) {
            // Если не получилось, используем Intent напрямую
            val kidLockIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(kidLockIntent)
            // Очищаем lastKnownPackageName после открытия KidLock
            handler.postDelayed({
                lastKnownPackageName = null
            }, 500)
        }
    }

    companion object {
        fun isServiceEnabled(context: android.content.Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val serviceName = "${context.packageName}/${ScreenTimeAccessibilityService::class.java.name}"
            return enabledServices.contains(serviceName)
        }
    }
}

