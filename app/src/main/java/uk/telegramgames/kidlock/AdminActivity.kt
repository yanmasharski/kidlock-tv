package uk.telegramgames.kidlock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import uk.telegramgames.kidlock.BuildConfig

class AdminActivity : AppCompatActivity() {
    private lateinit var viewModel: AdminViewModel

    private lateinit var btnExit: Button
    private lateinit var rvSections: RecyclerView
    private lateinit var sectionAdapter: AdminSectionAdapter
    
    private lateinit var layoutSectionDashboard: View
    private lateinit var layoutSectionSettings: View
    private lateinit var layoutSectionAccess: View
    private lateinit var layoutSectionSystem: View
    private lateinit var layoutSectionAbout: View
    
    private lateinit var tvRemainingTime: TextView
    private lateinit var tvLockStatus: TextView
    private lateinit var btnUnlock: Button
    
    private lateinit var btnSetLimit: Button
    private lateinit var btnIncreaseTime: Button
    private lateinit var btnDecreaseTime: Button
    private lateinit var switchAutostart: Switch
    private lateinit var switchBlocking: Switch
    
    private lateinit var etCodeCount: EditText
    private lateinit var etMinutesPerCode: EditText
    private lateinit var btnGenerateCodes: Button
    private lateinit var etNewPin: EditText
    private lateinit var btnChangePin: Button
    private lateinit var rvCodes: RecyclerView
    private lateinit var codeAdapter: CodeAdapter
    
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAccessibilityInstructions: TextView
    private lateinit var btnOpenAccessibilitySettings: Button
    private lateinit var tvUsageStatsStatus: TextView
    private lateinit var tvUsageStatsInstructions: TextView
    private lateinit var btnOpenUsageStatsSettings: Button
    
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvOverlayInstructions: TextView
    private lateinit var btnOpenOverlaySettings: Button
    
    private lateinit var tvAboutVersion: TextView
    private lateinit var btnViewLicense: Button
    private lateinit var btnViewSourceCode: Button
    
    private lateinit var tvMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        initViews()
        setupSections()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        Log.d("KidLock", "AdminActivity.onCreate() - loadCodes called")
        viewModel.loadCodes()

        setupPrimaryButtons()
    }

    private fun setupPrimaryButtons() {
        setupPrimaryButton(btnDecreaseTime)
        setupPrimaryButton(btnIncreaseTime)
        setupPrimaryButton(btnSetLimit)
        setupPrimaryButton(btnUnlock)
        
        setupPrimaryButton(btnGenerateCodes)
        setupPrimaryButton(btnChangePin)
        
        setupPrimaryButton(btnOpenAccessibilitySettings)
        setupPrimaryButton(btnOpenUsageStatsSettings)
        setupPrimaryButton(btnOpenOverlaySettings)
        
        setupPrimaryButton(btnViewLicense)
        setupPrimaryButton(btnViewSourceCode)
        
        setupPrimaryButton(btnExit)
    }

    private fun setupPrimaryButton(button: Button) {
        button.backgroundTintList = null
        button.isFocusable = true
        button.isClickable = true
    }

    private fun initViews() {
        btnExit = findViewById(R.id.btnExit)

        rvSections = findViewById(R.id.rvSections)

        layoutSectionDashboard = findViewById(R.id.layout_section_dashboard)
        layoutSectionSettings = findViewById(R.id.layout_section_settings)
        layoutSectionAccess = findViewById(R.id.layout_section_access)
        layoutSectionSystem = findViewById(R.id.layout_section_system)
        layoutSectionAbout = findViewById(R.id.layout_section_about)

        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        tvLockStatus = findViewById(R.id.tvLockStatus)
        btnUnlock = findViewById(R.id.btnUnlock)

        btnSetLimit = findViewById(R.id.btnSetLimit)
        btnIncreaseTime = findViewById(R.id.btnIncreaseTime)
        btnDecreaseTime = findViewById(R.id.btnDecreaseTime)
        switchAutostart = findViewById(R.id.switchAutostart)
        switchBlocking = findViewById(R.id.switchBlocking)

        etCodeCount = findViewById(R.id.etCodeCount)
        etMinutesPerCode = findViewById(R.id.etMinutesPerCode)
        btnGenerateCodes = findViewById(R.id.btnGenerateCodes)
        etNewPin = findViewById(R.id.etNewPin)
        btnChangePin = findViewById(R.id.btnChangePin)
        rvCodes = findViewById(R.id.rvCodes)

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvAccessibilityInstructions = findViewById(R.id.tvAccessibilityInstructions)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
        tvUsageStatsStatus = findViewById(R.id.tvUsageStatsStatus)
        tvUsageStatsInstructions = findViewById(R.id.tvUsageStatsInstructions)
        btnOpenUsageStatsSettings = findViewById(R.id.btnOpenUsageStatsSettings)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvOverlayInstructions = findViewById(R.id.tvOverlayInstructions)
        btnOpenOverlaySettings = findViewById(R.id.btnOpenOverlaySettings)

        tvAboutVersion = findViewById(R.id.tvAboutVersion)
        btnViewLicense = findViewById(R.id.btnViewLicense)
        btnViewSourceCode = findViewById(R.id.btnViewSourceCode)
        
        // Set app version
        tvAboutVersion.text = getString(R.string.about_version_format, BuildConfig.VERSION_NAME)

        tvMessage = findViewById(R.id.tvMessage)

        setupEditTextKeyboard(etCodeCount)
        setupEditTextKeyboard(etMinutesPerCode)
        setupEditTextKeyboard(etNewPin)
    }

    private fun setupEditTextKeyboard(editText: EditText) {
        editText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        editText.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupSections() {
        val sections = listOf(
            AdminSection(1, getString(R.string.section_dashboard), true),
            AdminSection(3, getString(R.string.section_access)),
            AdminSection(2, getString(R.string.section_settings)),
            AdminSection(4, getString(R.string.section_system)),
            AdminSection(6, getString(R.string.section_about)),
            AdminSection(5, getString(R.string.exit))
        )

        sectionAdapter = AdminSectionAdapter(sections) { section ->
            if (section.id == 5) {
                finish()
            } else {
                updateSectionVisibility(section.id)
            }
        }

        rvSections.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvSections.adapter = sectionAdapter
        
        updateSectionVisibility(1)
    }

    private fun updateSectionVisibility(sectionId: Int) {
        layoutSectionDashboard.visibility = if (sectionId == 1) View.VISIBLE else View.GONE
        layoutSectionSettings.visibility = if (sectionId == 2) View.VISIBLE else View.GONE
        layoutSectionAccess.visibility = if (sectionId == 3) View.VISIBLE else View.GONE
        layoutSectionSystem.visibility = if (sectionId == 4) View.VISIBLE else View.GONE
        layoutSectionAbout.visibility = if (sectionId == 6) View.VISIBLE else View.GONE
        
        when (sectionId) {
            1 -> btnSetLimit.requestFocus()
            2 -> etNewPin.requestFocus()
            3 -> {
                val hasCodes = (viewModel.codes.value?.isNotEmpty() == true)
                if (hasCodes) {
                    rvCodes.requestFocus()
                } else {
                    val isPaid = viewModel.isPaidVersion.value ?: false
                    if (isPaid) {
                        etCodeCount.requestFocus()
                    } else {
                        etMinutesPerCode.requestFocus()
                    }
                }
            }
            4 -> btnOpenAccessibilitySettings.requestFocus()
            6 -> btnViewLicense.requestFocus()
        }
    }

    private fun setupRecyclerView() {
        codeAdapter = CodeAdapter(emptyList()) { code ->
            viewModel.deleteCode(code)
        }
        rvCodes.layoutManager = LinearLayoutManager(this)
        rvCodes.adapter = codeAdapter
    }

    private fun setupObservers() {
        viewModel.dailyLimitMinutes.observe(this) { minutes ->
            tvRemainingTime.text = TimeManager.formatMinutes(this, minutes)
        }

        viewModel.remainingTimeMinutes.observe(this) { minutes ->
            val statusColor = if (minutes > 0) getColor(R.color.status_good) else getColor(R.color.status_bad)
            val statusText = if (minutes > 0) {
                getString(R.string.time_remaining_format, TimeManager.formatMinutes(this, minutes))
            } else {
                getString(R.string.time_expired)
            }
            tvLockStatus.text = statusText
            tvLockStatus.setTextColor(statusColor)
        }

        viewModel.codes.observe(this) { codes ->
            codeAdapter.updateCodes(codes)
            if (codes.isNotEmpty()) {
                rvCodes.scrollToPosition(0)
            }
        }

        viewModel.isAccessibilityServiceEnabled.observe(this) { enabled ->
            val statusText = if (enabled) getString(R.string.accessibility_enabled) else getString(R.string.accessibility_disabled)
            tvAccessibilityStatus.text = getString(R.string.accessibility_status_format, statusText)
            tvAccessibilityStatus.setTextColor(if (enabled) getColor(R.color.status_good) else getColor(R.color.status_bad))
            tvAccessibilityInstructions.visibility = if (enabled) View.GONE else View.VISIBLE
        }

        viewModel.isUsageStatsPermissionGranted.observe(this) { granted ->
            val statusText = if (granted) getString(R.string.usage_stats_granted) else getString(R.string.usage_stats_denied)
            tvUsageStatsStatus.text = getString(R.string.usage_stats_status_format, statusText)
            tvUsageStatsStatus.setTextColor(if (granted) getColor(R.color.status_good) else getColor(R.color.status_bad))
            tvUsageStatsInstructions.visibility = if (granted) View.GONE else View.VISIBLE
        }

        viewModel.isOverlayPermissionGranted.observe(this) { granted ->
            val statusText = if (granted) getString(R.string.usage_stats_granted) else getString(R.string.usage_stats_denied)
            tvOverlayStatus.text = getString(R.string.overlay_status_format, statusText)
            tvOverlayStatus.setTextColor(if (granted) getColor(R.color.status_good) else getColor(R.color.status_bad))
            tvOverlayInstructions.visibility = if (granted) View.GONE else View.VISIBLE
        }

        viewModel.isAutostartEnabled.observe(this) { enabled ->
            switchAutostart.isChecked = enabled
        }

        viewModel.isBlockingEnabled.observe(this) { enabled ->
            switchBlocking.isChecked = enabled
        }

        viewModel.canEnableBlocking.observe(this) { canEnable ->
            switchBlocking.isEnabled = canEnable
            if (!canEnable && switchBlocking.isChecked) {
                switchBlocking.isChecked = false
            }
        }

        viewModel.message.observe(this) { message ->
            tvMessage.text = message ?: ""
            tvMessage.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
            // Hide message after delay could be nice, but simple layout is fine for now
        }

        viewModel.isPaidVersion.observe(this) { isPaid ->
            updateCodeGenerationUI(isPaid)
        }

        viewModel.isUnlockedUntilTomorrow.observe(this) { isUnlocked ->
            btnUnlock.text = if (isUnlocked) {
                getString(R.string.lock_until_tomorrow)
            } else {
                getString(R.string.unlock_until_tomorrow)
            }
        }
    }

    private fun updateCodeGenerationUI(isPaid: Boolean) {
        if (isPaid) {
            // Paid version: show code count input with default 30
            etCodeCount.visibility = View.VISIBLE
            if (etCodeCount.text.isEmpty()) {
                etCodeCount.setText("30")
            }
            btnGenerateCodes.text = getString(R.string.generate_codes_paid)
        } else {
            // Free version: hide code count input, fixed 3 codes
            etCodeCount.visibility = View.GONE
            btnGenerateCodes.text = getString(R.string.generate_codes_free)
        }
    }

    private fun setupClickListeners() {
        btnSetLimit.setOnClickListener {
            val minutes = viewModel.dailyLimitMinutes.value ?: 0
            viewModel.setDailyLimitMinutes(minutes)
        }

        btnIncreaseTime.setOnClickListener {
            val current = viewModel.dailyLimitMinutes.value ?: 0
            viewModel.setDailyLimitMinutes(current + 15)
        }

        btnDecreaseTime.setOnClickListener {
            val current = viewModel.dailyLimitMinutes.value ?: 0
            if (current >= 15) {
                viewModel.setDailyLimitMinutes(current - 15)
            }
        }

        btnGenerateCodes.setOnClickListener {
            val isPaid = viewModel.isPaidVersion.value ?: false
            val count = if (isPaid) {
                etCodeCount.text.toString().toIntOrNull() ?: 30
            } else {
                3 // Free version: fixed 3 codes
            }
            val minutes = etMinutesPerCode.text.toString().toIntOrNull() ?: 30
            viewModel.generateCodes(count, minutes)
            if (isPaid) {
                etCodeCount.setText("30")
            }
        }

        btnChangePin.setOnClickListener {
            val newPin = etNewPin.text.toString()
            viewModel.changePin(newPin)
            etNewPin.setText("")
        }

        btnUnlock.setOnClickListener {
            viewModel.toggleUnlockUntilTomorrow()
        }

        btnOpenAccessibilitySettings.setOnClickListener {
            tvAccessibilityInstructions.visibility = View.VISIBLE
            viewModel.openAccessibilitySettings()
        }

        btnOpenUsageStatsSettings.setOnClickListener {
            tvUsageStatsInstructions.visibility = View.VISIBLE
            viewModel.openUsageStatsSettings()
        }

        btnOpenOverlaySettings.setOnClickListener {
            tvOverlayInstructions.visibility = View.VISIBLE
            viewModel.openOverlaySettings()
        }

        switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutostartEnabled(isChecked)
        }

        switchBlocking.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBlockingEnabled(isChecked)
        }

        btnViewLicense.setOnClickListener {
            openUrl(getString(R.string.about_license_url))
        }

        btnViewSourceCode.setOnClickListener {
            openUrl(getString(R.string.about_source_code_url))
        }

        btnExit.setOnClickListener {
            finish()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("KidLock", "Failed to open URL: $url", e)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSettings()
        viewModel.checkPermissions()
        viewModel.updateRemainingTime()
        viewModel.loadCodes()
    }
}

