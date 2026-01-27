package uk.telegramgames.kidlock

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)
    private val usageStatsHelper = UsageStatsHelper(application)

    private val _dailyLimitMinutes = MutableLiveData<Int>()
    val dailyLimitMinutes: LiveData<Int> = _dailyLimitMinutes

    private val _remainingTimeMinutes = MutableLiveData<Int>()
    val remainingTimeMinutes: LiveData<Int> = _remainingTimeMinutes

    private val _codes = MutableLiveData<List<Code>>()
    val codes: LiveData<List<Code>> = _codes

    private val _isAccessibilityServiceEnabled = MutableLiveData<Boolean>()
    val isAccessibilityServiceEnabled: LiveData<Boolean> = _isAccessibilityServiceEnabled

    private val _isUsageStatsPermissionGranted = MutableLiveData<Boolean>()
    val isUsageStatsPermissionGranted: LiveData<Boolean> = _isUsageStatsPermissionGranted

    private val _isAutostartEnabled = MutableLiveData<Boolean>()
    val isAutostartEnabled: LiveData<Boolean> = _isAutostartEnabled

    private val _isBlockingEnabled = MutableLiveData<Boolean>()
    val isBlockingEnabled: LiveData<Boolean> = _isBlockingEnabled

    private val _canEnableBlocking = MutableLiveData<Boolean>()
    val canEnableBlocking: LiveData<Boolean> = _canEnableBlocking

    private val _isPaidVersion = MutableLiveData<Boolean>()
    val isPaidVersion: LiveData<Boolean> = _isPaidVersion

    private val _isUnlockedUntilTomorrow = MutableLiveData<Boolean>()
    val isUnlockedUntilTomorrow: LiveData<Boolean> = _isUnlockedUntilTomorrow

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    init {
        loadSettings()
        loadCodes()
        checkPermissions()
        updateRemainingTime()
    }

    fun loadSettings() {
        // Check daily reset first before loading settings
        dataRepository.ensureDailyResetIfNeeded()
        
        _dailyLimitMinutes.value = dataRepository.getDailyTimeLimitMinutes()
        _isAutostartEnabled.value = dataRepository.isAutostartEnabled()
        _isPaidVersion.value = dataRepository.isPaidVersion()
        _isUnlockedUntilTomorrow.value = dataRepository.isUnlockedUntilTomorrow()

        val blockingEnabled = dataRepository.isBlockingEnabled()
        _isBlockingEnabled.value = blockingEnabled

        val isAccessibilityEnabled = ScreenTimeAccessibilityService.isServiceEnabled(
            getApplication()
        )
        val isUsageStatsGranted = usageStatsHelper.hasUsageStatsPermission()
        val allPermissionsGranted = isAccessibilityEnabled && isUsageStatsGranted
        _canEnableBlocking.value = allPermissionsGranted

        if (!allPermissionsGranted && blockingEnabled) {
            dataRepository.setBlockingEnabled(false)
            _isBlockingEnabled.value = false
        }
    }

    fun setDailyLimitMinutes(minutes: Int) {
        if (minutes < 0) {
            _message.value = getApplication<Application>().getString(R.string.limit_negative_error)
            return
        }
        dataRepository.setDailyTimeLimitMinutes(minutes)
        _dailyLimitMinutes.value = minutes
        val formattedTime = TimeManager.formatMinutes(getApplication(), minutes)
        _message.value = getApplication<Application>().getString(R.string.limit_set_format, formattedTime)
        updateRemainingTime()
    }

    fun generateCodes(count: Int, minutesPerCode: Int = 30) {
        Log.d("KidLock", "AdminViewModel.generateCodes() called: count=$count, minutesPerCode=$minutesPerCode")
        if (count <= 0 || count > 100) {
            Log.w("KidLock", "AdminViewModel.generateCodes() - invalid count: $count")
            _message.value = getApplication<Application>().getString(R.string.code_count_error)
            return
        }
        if (minutesPerCode < 0) {
            Log.w("KidLock", "AdminViewModel.generateCodes() - negative minutesPerCode: $minutesPerCode")
            _message.value = getApplication<Application>().getString(R.string.time_negative_error)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d("KidLock", "AdminViewModel.generateCodes() - generating codes on IO dispatcher")
            val newCodes = dataRepository.generateCodes(minutesPerCode, count)
            Log.d("KidLock", "AdminViewModel.generateCodes() - generated ${newCodes.size} codes: ${newCodes.map { it.value }}")
            withContext(Dispatchers.Main) {
                loadCodes()
                _message.value = getApplication<Application>().getString(
                    R.string.codes_generated_format,
                    newCodes.size,
                    minutesPerCode
                )
                Log.d("KidLock", "AdminViewModel.generateCodes() - finished, message set")
            }
        }
    }

    fun loadCodes() {
        Log.d("KidLock", "AdminViewModel.loadCodes() called")
        val allCodes = dataRepository.getCodes()
        Log.d("KidLock", "AdminViewModel.loadCodes() - loaded ${allCodes.size} codes: ${allCodes.map { "${it.value}(${it.addedTimeMinutes}min, used=${it.isUsed})" }}")
        _codes.postValue(allCodes)
        Log.d("KidLock", "AdminViewModel.loadCodes() - LiveData updated via postValue")
    }

    fun deleteCode(code: Code) {
        val codes = _codes.value?.toMutableList() ?: return
        codes.removeAll { it.value == code.value }
        dataRepository.saveCodes(codes)
        loadCodes()
        _message.value = getApplication<Application>().getString(R.string.code_deleted)
    }

    fun changePin(newPin: String) {
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
            _message.value = getApplication<Application>().getString(R.string.pin_format_error)
            return
        }
        dataRepository.setPin(newPin)
        _message.value = getApplication<Application>().getString(R.string.pin_changed)
    }

    fun toggleUnlockUntilTomorrow() {
        val isCurrentlyUnlocked = dataRepository.isUnlockedUntilTomorrow()
        
        if (isCurrentlyUnlocked) {
            // Lock until tomorrow: set large negative addedTime to zero out remaining time
            val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
            dataRepository.setAddedTimeMinutes(-dailyLimit - 1440)
            dataRepository.setUnlockedUntilTomorrow(false)
            _isUnlockedUntilTomorrow.value = false
            _message.value = getApplication<Application>().getString(R.string.time_locked)
        } else {
            // Unlock until tomorrow: add time until midnight
            val minutesUntilMidnight = TimeManager.getMinutesUntilMidnight()
            dataRepository.setAddedTimeMinutes(minutesUntilMidnight)
            dataRepository.setUnlockedUntilTomorrow(true)
            _isUnlockedUntilTomorrow.value = true
            _message.value = getApplication<Application>().getString(R.string.time_unlocked)
        }
        updateRemainingTime()
    }

    fun updateRemainingTime() {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if daily reset is needed (this also resets unlock state)
            val wasReset = dataRepository.ensureDailyResetIfNeeded()
            if (wasReset) {
                _isUnlockedUntilTomorrow.postValue(false)
            }

            val remaining = usageStatsHelper.getRemainingTimeMinutes(
                dataRepository.getDailyTimeLimitMinutes(),
                dataRepository.getAddedTimeMinutes()
            )

            _remainingTimeMinutes.postValue(remaining)
        }
    }

    fun checkPermissions() {
        val isAccessibilityEnabled = ScreenTimeAccessibilityService.isServiceEnabled(
            getApplication()
        )
        val isUsageStatsGranted = usageStatsHelper.hasUsageStatsPermission()

        _isAccessibilityServiceEnabled.value = isAccessibilityEnabled
        _isUsageStatsPermissionGranted.value = isUsageStatsGranted

        val allPermissionsGranted = isAccessibilityEnabled && isUsageStatsGranted
        _canEnableBlocking.value = allPermissionsGranted

        if (!allPermissionsGranted) {
            val currentBlockingState = dataRepository.isBlockingEnabled()
            if (currentBlockingState) {
                dataRepository.setBlockingEnabled(false)
                _isBlockingEnabled.value = false
            }
        }
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        getApplication<Application>().startActivity(intent)
    }

    fun openUsageStatsSettings() {
        usageStatsHelper.requestUsageStatsPermission()
    }

    fun setAutostartEnabled(enabled: Boolean) {
        dataRepository.setAutostartEnabled(enabled)
        _isAutostartEnabled.value = enabled
        _message.value = if (enabled) {
            getApplication<Application>().getString(R.string.autostart_enabled)
        } else {
            getApplication<Application>().getString(R.string.autostart_disabled)
        }
    }

    fun setBlockingEnabled(enabled: Boolean) {
        // Проверяем разрешения перед включением
        val isAccessibilityEnabled = _isAccessibilityServiceEnabled.value ?: false
        val isUsageStatsGranted = _isUsageStatsPermissionGranted.value ?: false
        val allPermissionsGranted = isAccessibilityEnabled && isUsageStatsGranted
        
        // Если пытаемся включить без разрешений, не позволяем
        if (enabled && !allPermissionsGranted) {
            _message.value = getApplication<Application>().getString(R.string.blocking_requires_permissions)
            return
        }
        
        dataRepository.setBlockingEnabled(enabled)
        _isBlockingEnabled.value = enabled
        _message.value = if (enabled) {
            getApplication<Application>().getString(R.string.blocking_enabled)
        } else {
            getApplication<Application>().getString(R.string.blocking_disabled)
        }
    }

    fun setPaidVersion(isPaid: Boolean) {
        dataRepository.setPaidVersion(isPaid)
        _isPaidVersion.value = isPaid
    }

    fun clearMessage() {
        _message.value = null
    }
}

