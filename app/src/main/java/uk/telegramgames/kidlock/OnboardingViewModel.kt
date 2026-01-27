package uk.telegramgames.kidlock

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)
    private val usageStatsHelper = UsageStatsHelper(application)

    private val _isAccessibilityServiceEnabled = MutableLiveData<Boolean>()
    val isAccessibilityServiceEnabled: LiveData<Boolean> = _isAccessibilityServiceEnabled

    private val _isUsageStatsPermissionGranted = MutableLiveData<Boolean>()
    val isUsageStatsPermissionGranted: LiveData<Boolean> = _isUsageStatsPermissionGranted

    private val _selectedDailyLimitMinutes = MutableLiveData<Int?>()
    val selectedDailyLimitMinutes: LiveData<Int?> = _selectedDailyLimitMinutes

    private val _generatedCodes = MutableLiveData<List<Code>>()
    val generatedCodes: LiveData<List<Code>> = _generatedCodes

    private val _pinErrorMessage = MutableLiveData<String?>()
    val pinErrorMessage: LiveData<String?> = _pinErrorMessage

    init {
        _selectedDailyLimitMinutes.value = null
        refreshPermissions()
    }

    fun refreshPermissions() {
        _isAccessibilityServiceEnabled.value = ScreenTimeAccessibilityService.isServiceEnabled(getApplication())
        _isUsageStatsPermissionGranted.value = usageStatsHelper.hasUsageStatsPermission()
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        getApplication<Application>().startActivity(intent)
    }

    fun openUsageStatsSettings() {
        usageStatsHelper.requestUsageStatsPermission()
    }

    fun setDailyLimitMinutes(minutes: Int) {
        if (minutes <= 0) {
            return
        }
        dataRepository.setDailyTimeLimitMinutes(minutes)
        _selectedDailyLimitMinutes.value = minutes
    }

    fun setAdminPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            _pinErrorMessage.value = getApplication<Application>().getString(R.string.pin_format_error)
            return false
        }
        dataRepository.setPin(pin)
        dataRepository.setOnboardingCompleted()
        maybeEnableBlockingAndAutostart()
        _pinErrorMessage.value = null
        return true
    }

    fun generateInitialCodes(): List<Code> {
        val codes = dataRepository.generateCodesWithMinutes(listOf(30, 60))
        _generatedCodes.value = codes
        return codes
    }

    fun clearPinError() {
        _pinErrorMessage.value = null
    }

    private fun maybeEnableBlockingAndAutostart() {
        refreshPermissions()
        val accessibilityEnabled = _isAccessibilityServiceEnabled.value == true
        val usageStatsGranted = _isUsageStatsPermissionGranted.value == true
        if (accessibilityEnabled && usageStatsGranted) {
            dataRepository.setBlockingEnabled(true)
            dataRepository.setAutostartEnabled(true)
        }
    }
}
