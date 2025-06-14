package com.dexxiez.ohlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.dexxiez.ohlauncher.data.Constants
import com.dexxiez.ohlauncher.data.Prefs
import com.dexxiez.ohlauncher.databinding.ActivityMainBinding
import com.dexxiez.ohlauncher.helper.getColorFromAttr
import com.dexxiez.ohlauncher.helper.isDarkThemeOn
import com.dexxiez.ohlauncher.helper.isDefaultLauncher
import com.dexxiez.ohlauncher.helper.isEinkDisplay
import com.dexxiez.ohlauncher.helper.isTablet
import com.dexxiez.ohlauncher.helper.resetLauncherViaFakeActivity
import com.dexxiez.ohlauncher.helper.setPlainWallpaper
import com.dexxiez.ohlauncher.helper.showLauncherSelector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null

    override fun onBackPressed() {
        if (navController.currentDestination?.id != R.id.mainFragment) super.onBackPressed()
    }

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        initClickListeners()
        initObservers(viewModel)
        viewModel.getAppList()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)
    }

    override fun onStart() {
        super.onStart()
        checkTheme()
    }

    override fun onStop() {
        backToHomeScreen()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        backToHomeScreen()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (prefs.dailyWallpaper &&
                        AppCompatDelegate.getDefaultNightMode() ==
                                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ) {
            setPlainWallpaper()
            viewModel.setWallpaperWorker()
            recreate()
        }
    }

    private fun initClickListeners() {
        binding.ivClose.setOnClickListener { binding.messageLayout.visibility = View.GONE }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) { openLauncherChooser(it) }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                    resetLauncherViaFakeActivity()
            else showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
        }
        viewModel.checkForMessages.observe(this) { checkForMessages() }
        viewModel.showDialog.observe(this) {
            when (it) {
                Constants.Dialog.HIDDEN -> {
                    showMessageDialog(
                            getString(R.string.hidden_apps),
                            getString(R.string.hidden_apps_message),
                            getString(R.string.okay)
                    ) { binding.messageLayout.visibility = View.GONE }
                }
                Constants.Dialog.KEYBOARD -> {
                    showMessageDialog(
                            getString(R.string.app_name),
                            getString(R.string.keyboard_message),
                            getString(R.string.okay)
                    ) { binding.messageLayout.visibility = View.GONE }
                }
                Constants.Dialog.DIGITAL_WELLBEING -> {
                    showMessageDialog(
                            getString(R.string.screen_time),
                            getString(R.string.app_usage_message),
                            getString(R.string.permission)
                    ) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }
            }
        }
    }

    private fun showMessageDialog(
            title: String,
            message: String,
            action: String,
            clickListener: () -> Unit
    ) {
        binding.tvTitle.text = title
        binding.tvMessage.text = message
        binding.tvAction.text = action
        binding.tvAction.setOnClickListener { clickListener() }
        binding.messageLayout.visibility = View.VISIBLE
    }

    private fun checkForMessages() {
        if (prefs.firstOpenTime == 0L) prefs.firstOpenTime = System.currentTimeMillis()

        // NOTE: Can use this later if i wanna hyjack

        // when (prefs.userState) {
        //     Constants.UserState.START -> {
        //         if (prefs.firstOpenTime.hasBeenMinutes(10))
        //                 prefs.userState = Constants.UserState.WALLPAPER
        //     }
        // }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O) return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        binding.messageLayout.visibility = View.GONE
        if (navController.currentDestination?.id != R.id.mainFragment)
                navController.popBackStack(R.id.mainFragment, false)
    }

    private fun setPlainWallpaper() {
        if (this.isDarkThemeOn()) setPlainWallpaper(this, android.R.color.black)
        else setPlainWallpaper(this, android.R.color.white)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun checkTheme() {
        timerJob?.cancel()
        timerJob =
                lifecycleScope.launch {
                    delay(200)
                    if ((prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES &&
                                    getColorFromAttr(R.attr.primaryColor) !=
                                            getColor(R.color.white)) ||
                                    (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_NO &&
                                            getColorFromAttr(R.attr.primaryColor) !=
                                                    getColor(R.color.black))
                    )
                            recreate()
                }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK) prefs.doubleTapEnabled = true
            }
            Constants.REQUEST_CODE_LAUNCHER_SELECTOR -> {
                if (resultCode == Activity.RESULT_OK) resetLauncherViaFakeActivity()
            }
        }
    }
}
