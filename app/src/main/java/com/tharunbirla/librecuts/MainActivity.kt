package com.tharunbirla.librecuts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Parcelable
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tharunbirla.librecuts.databinding.ActivityMainBinding
import com.tharunbirla.librecuts.utils.ErrorCode
import com.tharunbirla.librecuts.utils.ProjectEntry
import com.tharunbirla.librecuts.utils.ProjectStore
import com.tharunbirla.librecuts.utils.ThemeMode
import com.tharunbirla.librecuts.utils.applyGlassBlur
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tharunbirla.librecuts.utils.setBounceClickListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val selectVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.e("VideoSelection", "Could not take persistable permission", e)
                }
                Log.d("VideoSelection", "Video selected: $uri")
                navigateToEditingScreen(uri)
            } else {
                Log.e("VideoSelectionError", "No video selected")
            }
        }

    private val openProjectLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.e("ProjectSelection", "Could not take persistable permission for project URI", e)
                }
                Log.d("ProjectSelection", "Project selected: $uri")
                val intent = Intent(this, ProjectImportActivity::class.java).apply {
                    putExtra("PROJECT_URI", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                startActivity(intent)
            } else {
                Log.e("ProjectSelectionError", "No project selected")
            }
        }

    private val selectFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    val prefs = getSharedPreferences("librecuts_prefs", MODE_PRIVATE)
                    prefs.edit().putString("export_directory_uri", uri.toString()).apply()
                    updateExportFolderUI(uri, binding.tvCurrentExportFolder, R.string.str_default_movies_librecuts)
                } catch (e: Exception) {
                    Log.e("FolderSelectionError", "Error securing permission for URI", e)
                    showToast(getString(R.string.toast_failed_to_set_export_folder))
                }
            }
        }

    private val selectAudioFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    val prefs = getSharedPreferences("librecuts_prefs", MODE_PRIVATE)
                    prefs.edit().putString("export_audio_directory_uri", uri.toString()).apply()
                    updateExportFolderUI(uri, binding.tvCurrentAudioExportFolder, R.string.str_default_music_librecuts)
                } catch (e: Exception) {
                    Log.e("FolderSelectionError", "Error securing permission for URI", e)
                    showToast(getString(R.string.toast_failed_to_set_export_folder))
                }
            }
        }

    private val selectSnapshotFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    val prefs = getSharedPreferences("librecuts_prefs", MODE_PRIVATE)
                    prefs.edit().putString("export_snapshot_directory_uri", uri.toString()).apply()
                    updateExportFolderUI(uri, binding.tvCurrentSnapshotExportFolder, R.string.str_default_pictures_librecuts)
                } catch (e: Exception) {
                    Log.e("FolderSelectionError", "Error securing permission for URI", e)
                    showToast(getString(R.string.toast_failed_to_set_export_folder))
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先套用用户选择的明暗模式，保证首帧就是正确主题
        applyThemeMode()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnImport.setBounceClickListener {
            Log.d("ButtonClick", "Launching video selection.")
            selectVideo()
        }

        binding.btnOpenProject.setBounceClickListener {
            Log.d("ButtonClick", "Launching project selection.")
            openProjectLauncher.launch(arrayOf("*/*"))
        }

        // 悬浮导航：初始只有「项目」页带胶囊，其余状态由 switchTab 统一管理
        binding.tabSettings.background = null
        binding.tabAbout.background = null

        // Appearance（明暗模式）设置行
        updateThemeModeUI()
        binding.btnChangeTheme.setBounceClickListener {
            showThemeModeDialog()
        }

        // Setup bottom navigation tab switching (项目 / 设置 / 关于)
        binding.tabProjects.setBounceClickListener {
            switchTab(0)
        }
        
        binding.tabSettings.setBounceClickListener {
            switchTab(1)
        }

        binding.tabAbout.setBounceClickListener {
            switchTab(2)
        }
        
        // Setup Settings Actions
        binding.btnChangeExportFolder.setBounceClickListener {
            selectFolderLauncher.launch(null)
        }
        binding.btnChangeAudioExportFolder.setBounceClickListener {
            selectAudioFolderLauncher.launch(null)
        }
        binding.btnChangeSnapshotExportFolder.setBounceClickListener {
            selectSnapshotFolderLauncher.launch(null)
        }
        binding.btnChangeLanguage.setBounceClickListener {
            showLanguageDialog()
        }
        
        binding.btnCheckForUpdates.setBounceClickListener {
            checkForUpdates()
        }
        
        binding.btnOpenSourceLicenses.setBounceClickListener {
            com.mikepenz.aboutlibraries.LibsBuilder()
                .withActivityTitle(getString(R.string.str_open_source_licenses))
                .withSearchEnabled(true)
                .start(this)
        }
        
        // Initialize Settings UI
        val prefs = getSharedPreferences("librecuts_prefs", MODE_PRIVATE)
        val savedUriString = prefs.getString("export_directory_uri", null)
        if (savedUriString != null) {
            updateExportFolderUI(Uri.parse(savedUriString), binding.tvCurrentExportFolder, R.string.str_default_movies_librecuts)
        } else {
            updateExportFolderUI(null, binding.tvCurrentExportFolder, R.string.str_default_movies_librecuts)
        }

        val savedAudioUriString = prefs.getString("export_audio_directory_uri", null)
        if (savedAudioUriString != null) {
            updateExportFolderUI(Uri.parse(savedAudioUriString), binding.tvCurrentAudioExportFolder, R.string.str_default_music_librecuts)
        } else {
            updateExportFolderUI(null, binding.tvCurrentAudioExportFolder, R.string.str_default_music_librecuts)
        }

        val savedSnapshotUriString = prefs.getString("export_snapshot_directory_uri", null)
        if (savedSnapshotUriString != null) {
            updateExportFolderUI(Uri.parse(savedSnapshotUriString), binding.tvCurrentSnapshotExportFolder, R.string.str_default_pictures_librecuts)
        } else {
            updateExportFolderUI(null, binding.tvCurrentSnapshotExportFolder, R.string.str_default_pictures_librecuts)
        }

        updateLanguageUI()

        // Initialize Haptic Feedback preference
        updateHapticFeedbackUI()

        binding.btnToggleHapticFeedback.setBounceClickListener {
            val current = prefs.getBoolean("haptic_feedback", true)
            prefs.edit().putBoolean("haptic_feedback", !current).apply()
            updateHapticFeedbackUI()
        }

        // Initialize Fullscreen Editor preference
        updateFullscreenEditorUI()

        binding.btnToggleFullscreenEditor.setBounceClickListener {
            val current = prefs.getBoolean("fullscreen_editor", true)
            prefs.edit().putBoolean("fullscreen_editor", !current).apply()
            updateFullscreenEditorUI()
        }

        // Initialize Default Encoder preference
        updateEncoderUI()

        binding.btnChangeDefaultEncoder.setBounceClickListener {
            showEncoderDialog()
        }

        // Set dynamic About version tag
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvAboutVersion.text = "v${pInfo.versionName}"
        } catch (e: Exception) {
            binding.tvAboutVersion.text = "v1.0-beta5"
        }

        // Setup GitHub and Translation button listeners
        binding.btnStarGithub.setBounceClickListener {
            openUrl("https://github.com/tharunbirla/LibreCuts")
        }
        binding.btnTranslate.setBounceClickListener {
            openUrl("https://hosted.weblate.org/engage/librecuts/")
        }
        binding.btnReportBug.setBounceClickListener {
            openUrl("https://github.com/tharunbirla/LibreCuts/issues")
        }
        binding.btnSponsor.setBounceClickListener {
            openUrl("https://github.com/sponsors/tharunbirla")
        }

        // Onboarding / Welcome Dialog
        val isFirstLaunch = prefs.getBoolean("first_launch_v1", true)
        if (isFirstLaunch) {
            showOnboardingDialog(prefs)
        }

        // Handle shared/intent videos
        handleIntent(intent)

        // 毛玻璃：API 31+ 对背景光晕做真实模糊，低版本静默跳过
        binding.glassGlow.applyGlassBlur()

        // 初始化悬浮导航与最近项目列表（默认停在「项目」页）
        switchTab(0)
    }

    override fun onResume() {
        super.onResume()
        // 从编辑器保存工程返回后，刷新最近项目列表
        renderProjectList()
    }

    private fun updateExportFolderUI(uri: Uri?, textView: TextView, defaultStringResId: Int) {
        if (uri == null) {
            textView.text = getString(defaultStringResId)
        } else {
            try {
                val path = uri.lastPathSegment?.split(":")?.lastOrNull()
                if (!path.isNullOrEmpty()) {
                    textView.text = path
                } else {
                    textView.text = getString(R.string.str_custom_directory)
                }
            } catch (e: Exception) {
                textView.text = getString(R.string.str_custom_directory)
            }
        }
    }

    private data class LanguageItem(
        val tag: String,
        val displayName: String
    )

    private fun getAvailableLanguages(): List<LanguageItem> {
        val result = mutableListOf<LanguageItem>()
        result.add(LanguageItem("", getString(R.string.str_system_default)))

        val tags = mutableSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeConfig = android.app.LocaleConfig(this)
                val locales = localeConfig.supportedLocales
                if (locales != null) {
                    for (i in 0 until locales.size()) {
                        val locale = locales.get(i)
                        if (locale != null) {
                            tags.add(locale.toLanguageTag())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "LocaleConfig error", e)
            }
        }

        if (tags.isEmpty()) {
            try {
                val resId = resources.getIdentifier("_generated_res_locale_config", "xml", packageName)
                if (resId != 0) {
                    val parser = resources.getXml(resId)
                    var eventType = parser.eventType
                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                            val name = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                            if (!name.isNullOrEmpty()) {
                                tags.add(name)
                            }
                        }
                        eventType = parser.next()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "XmlParser _generated_res_locale_config error", e)
            }
        }

        if (tags.isEmpty()) {
            tags.addAll(listOf("en", "de", "et", "sk", "pt-BR"))
        }

        val items = tags.map { tag ->
            val locale = java.util.Locale.forLanguageTag(tag)
            val name = when (tag.lowercase()) {
                "pt-br" -> "Português (Brasil)"
                "zh-cn" -> "中文 (简体)"
                "zh-tw" -> "中文 (繁體)"
                else -> locale.getDisplayName(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            }
            LanguageItem(tag, name)
        }.sortedBy { it.displayName.lowercase() }

        result.addAll(items)
        return result
    }

    private fun updateLanguageUI() {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            binding.tvCurrentLanguage.text = getString(R.string.str_system_default)
        } else {
            val locale = currentLocales.get(0)
            val tag = locale?.toLanguageTag() ?: ""
            val name = when (tag.lowercase()) {
                "pt-br" -> "Português (Brasil)"
                else -> locale?.getDisplayName(locale)?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            }
            binding.tvCurrentLanguage.text = name ?: getString(R.string.str_system_default)
        }
    }

    private fun updateHapticFeedbackUI() {
        val prefs = getSharedPreferences("librecuts_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("haptic_feedback", true)
        binding.tvCurrentHapticFeedback.text = if (isEnabled) {
            getString(R.string.str_haptic_enabled_desc)
        } else {
            getString(R.string.str_haptic_disabled_desc)
        }
    }

    private fun updateFullscreenEditorUI() {
        val prefs = getSharedPreferences("librecuts_prefs", MODE_PRIVATE)
        val isFullscreen = prefs.getBoolean("fullscreen_editor", true)
        binding.tvCurrentFullscreenEditor.text = if (isFullscreen) {
            getString(R.string.str_fullscreen_enabled_desc)
        } else {
            getString(R.string.str_fullscreen_disabled_desc)
        }
    }

    private fun updateEncoderUI() {
        val prefs = getSharedPreferences("librecuts_prefs", MODE_PRIVATE)
        val defaultEncoder = prefs.getString("default_encoder", "hardware") ?: "hardware"
        if (defaultEncoder == "software") {
            binding.tvCurrentDefaultEncoder.text = getString(R.string.str_encoder_software)
        } else {
            binding.tvCurrentDefaultEncoder.text = getString(R.string.str_encoder_hardware)
        }
    }

    private fun showEncoderDialog() {
        val prefs = getSharedPreferences("librecuts_prefs", MODE_PRIVATE)
        val currentEncoder = prefs.getString("default_encoder", "hardware") ?: "hardware"
        val options = arrayOf(
            getString(R.string.str_encoder_hardware),
            getString(R.string.str_encoder_software)
        )
        val selectedIndex = if (currentEncoder == "software") 1 else 0

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.str_default_encoder)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val chosenEncoder = if (which == 1) "software" else "hardware"
                prefs.edit().putString("default_encoder", chosenEncoder).apply()
                updateEncoderUI()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.language_bottom_sheet_dialog, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.btnCloseSheet)?.setBounceClickListener {
            dialog.dismiss()
        }

        val container = view.findViewById<android.widget.LinearLayout>(R.id.layoutLanguageContainer)
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (currentLocales.isEmpty) "" else (currentLocales.get(0)?.toLanguageTag() ?: "")

        val availableLanguages = getAvailableLanguages()

        for (item in availableLanguages) {
            val itemView = layoutInflater.inflate(R.layout.item_language_selection, container, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvLanguageName)
            val ivCheck = itemView.findViewById<android.widget.ImageView>(R.id.ivCheckLanguage)

            tvName.text = item.displayName

            val isSelected = if (item.tag.isEmpty()) {
                currentTag.isEmpty()
            } else {
                currentTag.equals(item.tag, ignoreCase = true) ||
                (item.tag.length == 2 && currentTag.startsWith(item.tag, ignoreCase = true))
            }

            ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

            itemView.setBounceClickListener {
                val appLocale = if (item.tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(item.tag)
                }
                AppCompatDelegate.setApplicationLocales(appLocale)
                updateLanguageUI()
                dialog.dismiss()
            }

            container?.addView(itemView)
        }

        dialog.show()
    }

    private fun switchTab(tabIndex: Int) {
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_nav_active_pill)
        val activeColor = ContextCompat.getColor(this, R.color.colorPrimary)
        val inactiveColor = ContextCompat.getColor(this, R.color.inactiveTool)

        // Reset all tabs to inactive
        binding.layoutProjectsContent.visibility = View.GONE
        binding.layoutSettingsContent.visibility = View.GONE
        binding.layoutAboutContent.visibility = View.GONE

        binding.tabProjects.background = null
        binding.ivProjects.setColorFilter(inactiveColor)
        binding.tvProjectsLabel.setTextColor(inactiveColor)

        binding.tabSettings.background = null
        binding.ivSettings.setColorFilter(inactiveColor)
        binding.tvSettingsLabel.setTextColor(inactiveColor)

        binding.tabAbout.background = null
        binding.ivAbout.setColorFilter(inactiveColor)
        binding.tvAboutLabel.setTextColor(inactiveColor)

        when (tabIndex) {
            0 -> {
                binding.layoutProjectsContent.visibility = View.VISIBLE
                binding.tabProjects.background = activeBg
                binding.ivProjects.setColorFilter(activeColor)
                binding.tvProjectsLabel.setTextColor(activeColor)
                renderProjectList()
            }
            1 -> {
                binding.layoutSettingsContent.visibility = View.VISIBLE
                binding.tabSettings.background = activeBg
                binding.ivSettings.setColorFilter(activeColor)
                binding.tvSettingsLabel.setTextColor(activeColor)
            }
            2 -> {
                binding.layoutAboutContent.visibility = View.VISIBLE
                binding.tabAbout.background = activeBg
                binding.ivAbout.setColorFilter(activeColor)
                binding.tvAboutLabel.setTextColor(activeColor)
            }
        }
    }

    // ==================== 外观（明暗模式） ====================

    private fun applyThemeMode() {
        ThemeMode.apply(this)
    }

    private fun updateThemeModeUI() {
        binding.tvCurrentThemeMode.text = when (ThemeMode.current(this)) {
            ThemeMode.LIGHT -> getString(R.string.str_theme_light)
            ThemeMode.DARK -> getString(R.string.str_theme_dark)
            else -> getString(R.string.str_theme_follow_system)
        }
    }

    private fun showThemeModeDialog() {
        val modes = arrayOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
        val labels = arrayOf(
            getString(R.string.str_theme_follow_system),
            getString(R.string.str_theme_light),
            getString(R.string.str_theme_dark)
        )
        val selected = modes.indexOf(ThemeMode.current(this)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.str_theme_appearance)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                ThemeMode.save(this, modes[which])
                dialog.dismiss()
                // 模式变更会触发 Activity 重建，重建后由 updateThemeModeUI() 刷新文案
                applyThemeMode()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== 最近项目列表 ====================

    /** 渲染「最近项目」。数据来自 ProjectStore（应用私有目录的 JSON 索引）。 */
    private fun renderProjectList() {
        val container = binding.layoutProjectList ?: return
        container.removeAllViews()

        val entries: List<ProjectEntry> = try {
            ProjectStore.load(this)
        } catch (e: Exception) {
            Log.e("ProjectList", "load failed", e)
            emptyList()
        }

        binding.layoutProjectsEmpty.visibility =
            if (entries.isEmpty()) View.VISIBLE else View.GONE

        // 一次性取回持久化 URI 授权集合，用于廉价地判断记录是否已失效
        val grantedUris: Set<String> = try {
            contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

        for (entry in entries) {
            val row = layoutInflater.inflate(R.layout.item_project, container, false)
            val tvName = row.findViewById<TextView>(R.id.tvProjectName)
            val tvMeta = row.findViewById<TextView>(R.id.tvProjectMeta)
            val tvMissing = row.findViewById<TextView>(R.id.tvProjectMissing)
            val btnDelete = row.findViewById<View>(R.id.ivProjectDelete)

            tvName.text = entry.name

            val sizeText = if (entry.sizeBytes > 0) formatSize(entry.sizeBytes) + " · " else ""
            tvMeta.text = sizeText + formatDateTime(entry.lastModified)

            if (entry.uri !in grantedUris) {
                tvMissing.visibility = View.VISIBLE
            }

            row.setBounceClickListener {
                openProject(Uri.parse(entry.uri))
            }

            btnDelete.setBounceClickListener {
                ProjectStore.remove(this, entry.uri)
                showToast(getString(R.string.str_project_removed))
                renderProjectList()
            }

            container.addView(row)
        }
    }

    /** 进入工程：与「打开项目」完全同一条链路（ProjectImportActivity → 编辑器）。 */
    private fun openProject(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.d("ProjectOpen", "no persistable grant: ${e.message}")
        }
        try {
            val intent = Intent(this, ProjectImportActivity::class.java).apply {
                putExtra("PROJECT_URI", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("ProjectOpen", "open failed", e)
            showToast(getString(R.string.str_project_missing))
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        return if (kb < 1024) {
            String.format(Locale.US, "%.0f KB", kb)
        } else {
            String.format(Locale.US, "%.1f MB", kb / 1024.0)
        }
    }

    private fun formatDateTime(millis: Long): String {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
        } catch (e: Exception) {
            ""
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            showToast("Unable to open link")
        }
    }





    private fun selectVideo() {
        Log.d("VideoSelection", "Launching video picker.")
        val picker = com.tharunbirla.librecuts.customviews.MediaPickerBottomSheet().apply {
            initialMediaType = com.tharunbirla.librecuts.customviews.MediaPickerBottomSheet.MediaType.VIDEO
            showCategoryTabs = true
            showAudioTab = false
            onMediaSelectedListener = { uri ->
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.d("VideoSelection", "Could not take persistable permission: ${e.message}")
                }
                navigateToEditingScreen(uri)
            }
            onBrowseSystemFoldersRequested = {
                selectVideoLauncher.launch(arrayOf("video/*", "image/*"))
            }
        }
        picker.show(supportFragmentManager, "MediaPickerBottomSheet")
    }

    private fun navigateToEditingScreen(videoUri: Uri) {
        Log.d("Navigation", "Navigating to editing screen with URI: $videoUri")
        val intent = Intent(this, VideoEditingActivity::class.java).apply {
            putExtra("VIDEO_URI", videoUri)
            data = videoUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("video/") || type.startsWith("image/")) {
                (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                    Log.d("SharedVideo", "Received SEND intent with media URI: $uri")
                    navigateToEditingScreen(uri)
                }
            }
        } else if ((Intent.ACTION_VIEW == action || Intent.ACTION_EDIT == action) && type != null) {
            if (type.startsWith("video/") || type.startsWith("image/")) {
                intent.data?.let { uri ->
                    Log.d("SharedVideo", "Received VIEW/EDIT intent with media URI: $uri")
                    navigateToEditingScreen(uri)
                }
            }
        }
    }

    private fun showOnboardingDialog(prefs: android.content.SharedPreferences) {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_welcome_onboarding, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        dialog.window?.let { window ->
            // Make dialog window background transparent so our custom layout's background card and shape render perfectly
            window.setBackgroundDrawableResource(android.R.color.transparent)
            
            // Set size parameters
            val lp = window.attributes
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = lp
        }

        // Set version dynamically
        val tvVersion = view.findViewById<TextView>(R.id.tvOnboardingVersion)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "Version ${pInfo.versionName}"
        } catch (e: Exception) {
            tvVersion.text = "Version 1.0-beta4"
        }

        view.findViewById<View>(R.id.layoutStarGithub)?.setBounceClickListener {
            openUrl("https://github.com/tharunbirla/LibreCuts")
        }

        view.findViewById<View>(R.id.layoutSponsorGithub)?.setBounceClickListener {
            openUrl("https://github.com/sponsors/tharunbirla")
        }

        view.findViewById<View>(R.id.layoutDiscord)?.setBounceClickListener {
            openUrl("https://discord.gg/gwr3nE7YW")
        }

        view.findViewById<View>(R.id.layoutTroubleshooting)?.setBounceClickListener {
            openUrl("https://github.com/tharunbirla/LibreCuts/wiki/Error-Codes-&-Troubleshooting")
        }

        view.findViewById<View>(R.id.btnOnboardingGetStarted)?.setBounceClickListener {
            prefs.edit().putBoolean("first_launch_v1", false).apply()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showToast(message: String) {
        Log.d("ToastMessage", "Showing toast: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun checkForUpdates() {
        showToast("Checking for updates in browser...")
        openUrl("https://github.com/tharunbirla/LibreCuts/releases/latest")
    }
}