package uk.telegramgames.kidlock

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Date

class DataRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "kidlock_prefs"
        private const val KEY_PIN = "pin"
        private const val KEY_DAILY_LIMIT = "daily_limit_minutes"
        private const val KEY_CODES = "codes"
        private const val KEY_REMAINING_TIME = "remaining_time_minutes"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_ADDED_TIME = "added_time_minutes"
        private const val KEY_AUTOSTART_ENABLED = "autostart_enabled"
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"
        private const val KEY_PAID_VERSION = "paid_version"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_UNLOCKED_UNTIL_TOMORROW = "unlocked_until_tomorrow"
        private const val DEFAULT_PIN = "000000"

        @Volatile
        private var INSTANCE: DataRepository? = null

        fun getInstance(context: Context): DataRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DataRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun getPin(): String {
        return prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        val savedFlag = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        return savedFlag || getPin() != DEFAULT_PIN
    }

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    fun getDailyTimeLimitMinutes(): Int {
        return prefs.getInt(KEY_DAILY_LIMIT, 60)
    }

    fun setDailyTimeLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT, minutes).apply()
    }

    fun getCodes(): List<Code> {
        val codesJson = prefs.getString(KEY_CODES, "") ?: ""
        Log.d("KidLock", "DataRepository.getCodes() - codesJson длина: ${codesJson.length}, содержимое: '$codesJson'")
        if (codesJson.isEmpty()) {
            Log.d("KidLock", "DataRepository.getCodes() - codesJson пустой, возвращаем emptyList()")
            return emptyList()
        }

        val codes = codesJson.split(";")
            .mapNotNull { Code.fromJsonString(it) }
        Log.d("KidLock", "DataRepository.getCodes() - распарсено ${codes.size} кодов: ${codes.map { "${it.value}(${it.addedTimeMinutes}мин, used=${it.isUsed})" }}")
        return codes
    }

    fun saveCodes(codes: List<Code>) {
        val codesJson = codes.joinToString(";") { it.toJsonString() }
        Log.d("KidLock", "DataRepository.saveCodes() - сохраняем ${codes.size} кодов: ${codes.map { "${it.value}(${it.addedTimeMinutes}мин, used=${it.isUsed})" }}")
        Log.d("KidLock", "DataRepository.saveCodes() - codesJson длина: ${codesJson.length}, содержимое: '$codesJson'")
        val success = prefs.edit().putString(KEY_CODES, codesJson).commit()
        if (!success) {
            Log.w("KidLock", "DataRepository.saveCodes() - commit failed, falling back to apply()")
            prefs.edit().putString(KEY_CODES, codesJson).apply()
        } else {
            Log.d("KidLock", "DataRepository.saveCodes() - коды успешно сохранены через commit")
        }
    }

    fun addCode(code: Code) {
        val codes = getCodes().toMutableList()
        codes.add(code)
        saveCodes(codes)
    }

    fun updateCode(updatedCode: Code) {
        val codes = getCodes().toMutableList()
        val index = codes.indexOfFirst { it.value == updatedCode.value }
        if (index >= 0) {
            codes[index] = updatedCode
            saveCodes(codes)
        }
    }

    fun findCode(value: String): Code? {
        return getCodes().firstOrNull { it.value == value }
    }

    fun getRemainingTimeMinutes(): Int {
        return prefs.getInt(KEY_REMAINING_TIME, 0)
    }

    fun setRemainingTimeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_REMAINING_TIME, minutes).apply()
    }

    fun addTimeMinutes(minutes: Int) {
        val current = getRemainingTimeMinutes()
        setRemainingTimeMinutes(current + minutes)
    }

    fun resetRemainingTime() {
        setRemainingTimeMinutes(0)
    }

    fun getLastResetDate(): Long {
        return prefs.getLong(KEY_LAST_RESET_DATE, 0)
    }

    fun setLastResetDate(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_RESET_DATE, timestamp).apply()
    }

    fun getAddedTimeMinutes(): Int {
        return prefs.getInt(KEY_ADDED_TIME, 0)
    }

    fun setAddedTimeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_ADDED_TIME, minutes).apply()
    }

    fun addToAddedTime(minutes: Int) {
        val current = getAddedTimeMinutes()
        setAddedTimeMinutes(current + minutes)
    }

    fun resetAddedTime() {
        setAddedTimeMinutes(0)
    }

    fun isAutostartEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTOSTART_ENABLED, false)
    }

    fun setAutostartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOSTART_ENABLED, enabled).apply()
    }

    fun isBlockingEnabled(): Boolean {
        return prefs.getBoolean(KEY_BLOCKING_ENABLED, true)
    }

    fun setBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCKING_ENABLED, enabled).apply()
    }

    fun isPaidVersion(): Boolean {
        return prefs.getBoolean(KEY_PAID_VERSION, false)
    }

    fun setPaidVersion(isPaid: Boolean) {
        prefs.edit().putBoolean(KEY_PAID_VERSION, isPaid).apply()
    }

    fun isUnlockedUntilTomorrow(): Boolean {
        return prefs.getBoolean(KEY_UNLOCKED_UNTIL_TOMORROW, false)
    }

    fun setUnlockedUntilTomorrow(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_UNLOCKED_UNTIL_TOMORROW, unlocked).apply()
    }

    fun generateCodes(minutesPerCode: Int, count: Int): List<Code> {
        Log.d("KidLock", "DataRepository.generateCodes() called: minutesPerCode=$minutesPerCode, count=$count")

        val oldCodesCount = getCodes().size
        Log.d("KidLock", "DataRepository.generateCodes() - clearing old codes: $oldCodesCount items")
        saveCodes(emptyList())
        
        val codes = generateCodesInternal(List(count) { minutesPerCode })

        Log.d("KidLock", "DataRepository.generateCodes() - saving ${codes.size} new codes")
        saveCodes(codes)
        Log.d("KidLock", "DataRepository.generateCodes() - returning ${codes.size} new codes: ${codes.map { it.value }}")
        return codes
    }

    fun generateCodesWithMinutes(minutesList: List<Int>): List<Code> {
        Log.d("KidLock", "DataRepository.generateCodesWithMinutes() called: minutesList=$minutesList")

        val oldCodesCount = getCodes().size
        Log.d("KidLock", "DataRepository.generateCodesWithMinutes() - clearing old codes: $oldCodesCount items")
        saveCodes(emptyList())

        val codes = generateCodesInternal(minutesList)
        Log.d("KidLock", "DataRepository.generateCodesWithMinutes() - saving ${codes.size} new codes")
        saveCodes(codes)
        Log.d("KidLock", "DataRepository.generateCodesWithMinutes() - returning ${codes.size} new codes: ${codes.map { it.value }}")
        return codes
    }

    fun resetDailyData() {
        resetRemainingTime()
        resetAddedTime()
        setUnlockedUntilTomorrow(false)
        setLastResetDate(TimeManager.getTodayStartTime())
    }

    fun ensureDailyResetIfNeeded(): Boolean {
        val lastReset = getLastResetDate()
        return if (TimeManager.shouldResetDailyLimit(lastReset)) {
            resetDailyData()
            true
        } else {
            false
        }
    }

    fun initializeIfNeeded() {
        if (getLastResetDate() == 0L) {
            setLastResetDate(TimeManager.getTodayStartTime())
        }
        val currentPin = getPin()
        if (currentPin.isEmpty()) {
            setPin(DEFAULT_PIN)
        }
        
        if (currentPin.length == 4) {
            Log.d("KidLock", "Migration: resetting obsolete 4-digit PIN to $DEFAULT_PIN")
            setPin(DEFAULT_PIN)
        }
        
        // Миграция: очищаем устаревшие 4-значные коды
        val codes = getCodes()
        if (codes.isNotEmpty() && codes.any { it.value.length == 4 }) {
            Log.d("KidLock", "Migration: clearing obsolete 4-digit codes (${codes.count { it.value.length == 4 }} found)")
            saveCodes(emptyList())
        }
    }

    private fun generateCodesInternal(minutesList: List<Int>): List<Code> {
        val chars = "0123456789"
        val codes = mutableListOf<Code>()
        val existing = mutableSetOf<String>()
        val currentPin = getPin()

        minutesList.forEach { minutes ->
            var codeValue: String
            do {
                codeValue = (1..6).map { chars.random() }.joinToString("")
            } while (existing.contains(codeValue) || codeValue == currentPin)

            existing.add(codeValue)
            codes.add(Code(value = codeValue, addedTimeMinutes = minutes))
            Log.d("KidLock", "DataRepository.generateCodesInternal() - generated code: $codeValue (${minutes} min)")
        }

        return codes
    }
}

