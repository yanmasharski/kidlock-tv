package uk.telegramgames.kidlock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: CodeInputViewModel
    private lateinit var etCodeInput: EditText
    private lateinit var tvRemainingTime: TextView
    private lateinit var tvMessage: TextView
    private lateinit var btnAdmin: Button

    private var codeInputWatcher: TextWatcher? = null
    private var isUpdatingCodeInput = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataRepository = DataRepository.getInstance(this)
        dataRepository.initializeIfNeeded()

        if (!dataRepository.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[CodeInputViewModel::class.java]

        etCodeInput = findViewById(R.id.etCodeInput)
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        tvMessage = findViewById(R.id.tvMessage)
        btnAdmin = findViewById(R.id.btnAdmin)

        setupObservers()
        setupCodeInput()

        btnAdmin.setOnLongClickListener {
            startActivity(Intent(this, PinInputActivity::class.java))
            true
        }

        etCodeInput.post {
            etCodeInput.requestFocus()
        }
    }

    private fun setupObservers() {
        viewModel.codeInput.observe(this) { code ->
            displayCode(code)
        }

        viewModel.remainingTimeMinutes.observe(this) { minutes ->
            tvRemainingTime.text = getString(R.string.remaining_time_format, TimeManager.formatMinutes(this, minutes))
        }

        viewModel.message.observe(this) { message ->
            tvMessage.text = message ?: ""
            tvMessage.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isCodeValid.observe(this) { isValid ->
            if (isValid) {
                tvMessage.setTextColor(getColor(R.color.status_good))
            } else {
                tvMessage.setTextColor(getColor(R.color.status_bad))
            }
        }

        viewModel.shouldOpenAdmin.observe(this) { shouldOpen ->
            if (shouldOpen) {
                startActivity(Intent(this, AdminActivity::class.java))
                viewModel.clearShouldOpenAdmin()
            }
        }
    }

    private fun addChar(char: Char) {
        viewModel.addCharToCode(char)
    }

    private fun removeChar() {
        viewModel.removeCharFromCode()
    }

    private fun displayCode(code: String) {
        if (!isUpdatingCodeInput && etCodeInput.text.toString() != code) {
            isUpdatingCodeInput = true
            etCodeInput.removeTextChangedListener(codeInputWatcher)
            etCodeInput.setText(code)
            etCodeInput.setSelection(code.length)
            etCodeInput.addTextChangedListener(codeInputWatcher)
            isUpdatingCodeInput = false
        }
    }

    private fun activateCode() {
        viewModel.activateCode()
    }

    private fun setupCodeInput() {
        codeInputWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingCodeInput) return

                val text = s?.toString()?.filter { it.isDigit() } ?: ""

                val limitedText = if (text.length > 6) text.substring(0, 6) else text
                
                val currentCode = viewModel.codeInput.value ?: ""
                
                if (limitedText != currentCode) {
                    val oldCode = viewModel.codeInput.value ?: ""
                    repeat(oldCode.length) {
                        removeChar()
                    }
                    limitedText.forEach { char ->
                        addChar(char)
                    }
                }
            }
        }
        
        etCodeInput.addTextChangedListener(codeInputWatcher)

        viewModel.codeInput.observe(this) { code ->
            if (!isUpdatingCodeInput && etCodeInput.text.toString() != code) {
                isUpdatingCodeInput = true
                etCodeInput.removeTextChangedListener(codeInputWatcher)
                etCodeInput.setText(code)
                etCodeInput.setSelection(code.length)
                etCodeInput.addTextChangedListener(codeInputWatcher)
                isUpdatingCodeInput = false
            }
        }

        etCodeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                activateCode()
                true
            } else {
                false
            }
        }
        
        etCodeInput.setOnFocusChangeListener { view, hasFocus ->
            etCodeInput.setCursorVisible(hasFocus)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (hasFocus) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateRemainingTime()
        
        // Восстанавливаем фокус на поле ввода и показываем системную клавиатуру
        etCodeInput.post {
            if (!etCodeInput.hasFocus()) {
                etCodeInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etCodeInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            // Обработка навигации пультом ДУ
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // При навигации вниз с EditText скрываем клавиатуру для выбора кнопок
                    val currentFocus = currentFocus
                    if (currentFocus == etCodeInput && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(etCodeInput.windowToken, 0)
                    }
                    // Разрешаем стандартную обработку навигации
                    return super.dispatchKeyEvent(event)
                }
            }
            
            // Обработка ввода цифр с клавиатуры или пульта ДУ
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    removeChar()
                    return true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    activateCode()
                    return true
                }
                KeyEvent.KEYCODE_0 -> { addChar('0'); return true }
                KeyEvent.KEYCODE_1 -> { addChar('1'); return true }
                KeyEvent.KEYCODE_2 -> { addChar('2'); return true }
                KeyEvent.KEYCODE_3 -> { addChar('3'); return true }
                KeyEvent.KEYCODE_4 -> { addChar('4'); return true }
                KeyEvent.KEYCODE_5 -> { addChar('5'); return true }
                KeyEvent.KEYCODE_6 -> { addChar('6'); return true }
                KeyEvent.KEYCODE_7 -> { addChar('7'); return true }
                KeyEvent.KEYCODE_8 -> { addChar('8'); return true }
                KeyEvent.KEYCODE_9 -> { addChar('9'); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
