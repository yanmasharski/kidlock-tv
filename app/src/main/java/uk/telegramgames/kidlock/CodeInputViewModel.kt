package uk.telegramgames.kidlock

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class CodeInputViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)
    private val usageStatsHelper = UsageStatsHelper(application)

    private val _remainingTimeMinutes = MutableLiveData<Int>()
    val remainingTimeMinutes: LiveData<Int> = _remainingTimeMinutes

    private val _codeInput = MutableLiveData<String>()
    val codeInput: LiveData<String> = _codeInput

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _isCodeValid = MutableLiveData<Boolean>()
    val isCodeValid: LiveData<Boolean> = _isCodeValid

    private val _shouldOpenAdmin = MutableLiveData<Boolean>()
    val shouldOpenAdmin: LiveData<Boolean> = _shouldOpenAdmin

    init {
        updateRemainingTime()
    }

    fun setCodeInput(input: String) {
        _codeInput.value = input
        _message.value = null
    }

    fun addCharToCode(char: Char) {
        val current = _codeInput.value ?: ""
        if (current.length < 6) {
            val newCode = current + char
            _codeInput.value = newCode

            // Automatically validate as soon as the 6th character is entered
            if (newCode.length == 6) {
                activateCode()
            }
        }
    }

    fun removeCharFromCode() {
        val current = _codeInput.value ?: ""
        if (current.isNotEmpty()) {
            _codeInput.value = current.dropLast(1)
        }
    }

    fun clearCode() {
        _codeInput.value = ""
        _message.value = null
    }

    fun activateCode() {
        val codeValue = _codeInput.value ?: ""
        if (codeValue.length != 6) {
            _message.value = getApplication<Application>().getString(R.string.code_length_error)
            _isCodeValid.value = false
            return
        }

        dataRepository.ensureDailyResetIfNeeded()

        val code = dataRepository.findCode(codeValue)
        if (code != null) {
            if (code.isUsed) {
                _message.value = getApplication<Application>().getString(R.string.code_already_used_error)
                _isCodeValid.value = false
                return
            }

            val updatedCode = code.copy(
                isUsed = true,
                usedDate = Date()
            )
            dataRepository.updateCode(updatedCode)

            if (code.addedTimeMinutes > 0) {
                val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
                val currentAddedTime = dataRepository.getAddedTimeMinutes()

                val usedTimeMillis = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    usageStatsHelper.getTodayUsageTimeMillis()
                } else {
                    0L
                }
                val usedTimeMinutes = TimeManager.millisToMinutes(usedTimeMillis)

                val extraUsed = (usedTimeMinutes - dailyLimit).coerceAtLeast(0)

                val uncoveredDebt = (extraUsed - currentAddedTime).coerceAtLeast(0)

                val totalToAdd = code.addedTimeMinutes + uncoveredDebt

                if (uncoveredDebt > 0) {
                    Log.d("KidLock", "Compensating usage debt: used=$usedTimeMinutes, limit=$dailyLimit, extraUsed=$extraUsed, currentAdded=$currentAddedTime, debt=$uncoveredDebt. Adding $totalToAdd ($code.addedTimeMinutes + $uncoveredDebt)")
                }

                dataRepository.addToAddedTime(totalToAdd)
            }

            _message.value = getApplication<Application>().getString(R.string.code_activated_format, code.addedTimeMinutes)
            _isCodeValid.value = true
            clearCode()
            updateRemainingTime()
            return
        }

        val pin = dataRepository.getPin()
        if (codeValue == pin) {
            _shouldOpenAdmin.value = true
            clearCode()
            return
        }

        _message.value = getApplication<Application>().getString(R.string.code_not_found_error)
        _isCodeValid.value = false
    }

    fun updateRemainingTime() {
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.ensureDailyResetIfNeeded()

            val remaining = usageStatsHelper.getRemainingTimeMinutes(
                dataRepository.getDailyTimeLimitMinutes(),
                dataRepository.getAddedTimeMinutes()
            )

            _remainingTimeMinutes.postValue(remaining)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearShouldOpenAdmin() {
        _shouldOpenAdmin.value = false
    }
}

