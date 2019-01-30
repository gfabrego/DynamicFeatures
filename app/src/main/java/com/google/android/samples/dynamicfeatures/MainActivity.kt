/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.samples.dynamicfeatures

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus

private const val packageName = "com.google.android.samples.dynamicfeatures.ondemand"
private const val kotlinSampleClassname = "$packageName.KotlinSampleActivity"
private const val javaSampleClassname = "$packageName.JavaSampleActivity"
private const val nativeSampleClassname = "$packageName.NativeSampleActivity"
private const val heavySampleClassName = "$packageName.heavy.HeavyActivity"

/** Activity that displays buttons and handles loading of feature modules. */
class MainActivity : AppCompatActivity() {

    private val splitInstallManager by lazy { SplitInstallManagerFactory.create(this) }
    private val moduleAssets by lazy { getString(R.string.module_assets) }
    private val moduleKotlin by lazy { getString(R.string.module_kotlin) }
    private val moduleJava by lazy { getString(R.string.module_java) }
    private val moduleNative by lazy { getString(R.string.module_native) }
    private val moduleHeavy by lazy { getString(R.string.module_heavy) }

    private lateinit var progress: Group
    private lateinit var buttons: Group
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private val clickListener by lazy { buildClickListener() }
    private val modulesInstallStateUpdateListener by lazy { buildStateUpdateListener() }

    private fun buildStateUpdateListener(): SplitInstallStateUpdatedListener =
        SplitInstallStateUpdatedListener { state ->
            val multiInstall = state.moduleNames().size > 1
            state.moduleNames().forEach { name ->
                when (state.status()) {
                    //  In order to see this, the application has to be uploaded to the Play Store.
                    SplitInstallSessionStatus.DOWNLOADING -> displayLoadingState(state, "Downloading $name")
                    /*
                    This may occur when attempting to download a sufficiently large module.

                    In order to see this, the application has to be uploaded to the Play Store.
                    Then features can be requested until the confirmation path is triggered.
                    */
                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION ->
                        startIntentSender(state.resolutionIntent()?.intentSender, null, 0, 0, 0)
                    SplitInstallSessionStatus.INSTALLED -> onSuccessfulLoad(name, launch = !multiInstall)
                    SplitInstallSessionStatus.INSTALLING -> displayLoadingState(state, "Installing $name")
                    SplitInstallSessionStatus.CANCELED -> {
                        toastAndLog("Installation cancelled")
                        displayButtons()
                    }
                    SplitInstallSessionStatus.FAILED ->
                        toastAndLog("Error: ${state.errorCode()} for module ${state.moduleNames()}")
                }
            }
        }

    private fun buildClickListener(): View.OnClickListener =
        View.OnClickListener {
            when (it.id) {
                R.id.btn_load_kotlin -> loadAndLaunchModule(moduleKotlin)
                R.id.btn_load_java -> loadAndLaunchModule(moduleJava)
                R.id.btn_load_native -> loadAndLaunchModule(moduleNative)
                R.id.btn_load_assets -> loadAndLaunchModule(moduleAssets)
                R.id.btn_load_heavy -> loadAndLaunchModule(moduleHeavy)
                R.id.btn_install_all_now -> installAllFeaturesNow()
                R.id.btn_install_all_deferred -> installAllFeaturesDeferred()
                R.id.btn_request_uninstall -> requestUninstall()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeViews()
    }

    override fun onResume() {
        super.onResume()
        splitInstallManager.registerListener(modulesInstallStateUpdateListener)
    }

    override fun onPause() {
        splitInstallManager.unregisterListener(modulesInstallStateUpdateListener)
        super.onPause()
    }

    private fun displayAssets() {
        // Get the asset manager with a refreshed context, to access content of newly installed apk.
        val assetManager = createPackageContext(packageName, 0).assets
        // Now treat it like any other asset file.
        val assets = assetManager.open("assets.txt")
        val assetContent = assets.bufferedReader().use { it.readText() }
        AlertDialog.Builder(this).setTitle("Asset content").setMessage(assetContent).show()
    }

    /** Launch an activity by its class name. */
    private fun launchActivity(className: String) {
        Intent().setClassName(packageName, className)
            .also {
                startActivity(it)
            }
    }

    /** Set up all view variables. */
    private fun initializeViews() {
        bindViews()
        setupClickListener()
    }

    private fun bindViews() {
        buttons = findViewById(R.id.buttons)
        progress = findViewById(R.id.progress)
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)
    }

    /** Set all click listeners required for the buttons on the UI. */
    private fun setupClickListener() {
        setClickListener(R.id.btn_load_kotlin, clickListener)
        setClickListener(R.id.btn_load_java, clickListener)
        setClickListener(R.id.btn_load_assets, clickListener)
        setClickListener(R.id.btn_load_native, clickListener)
        setClickListener(R.id.btn_load_heavy, clickListener)
        setClickListener(R.id.btn_install_all_now, clickListener)
        setClickListener(R.id.btn_install_all_deferred, clickListener)
        setClickListener(R.id.btn_request_uninstall, clickListener)
    }

    private fun setClickListener(id: Int, listener: View.OnClickListener) {
        findViewById<View>(id).setOnClickListener(listener)
    }

    private fun updateProgressMessage(message: String) {
        if (progress.visibility != View.VISIBLE) displayProgress()
        progressText.text = message
    }

    private fun displayProgress() {
        progress.visibility = View.VISIBLE
        buttons.visibility = View.GONE
    }

    private fun displayButtons() {
        progress.visibility = View.GONE
        buttons.visibility = View.VISIBLE
    }

    private fun displayLoadingState(state: SplitInstallSessionState, message: String) {
        displayProgress()
        progressBar.max = state.totalBytesToDownload().toInt()
        progressBar.progress = state.bytesDownloaded().toInt()
        updateProgressMessage(message)
    }

    private fun onSuccessfulLoad(moduleName: String, launch: Boolean) {
        if (launch) {
            when (moduleName) {
                moduleKotlin -> launchActivity(kotlinSampleClassname)
                moduleJava -> launchActivity(javaSampleClassname)
                moduleNative -> launchActivity(nativeSampleClassname)
                moduleAssets -> displayAssets()
                moduleHeavy -> launchActivity(heavySampleClassName)
            }
        }
        displayButtons()
    }

    private fun loadAndLaunchModule(moduleName: String) {
        updateProgressMessage("Loading module $moduleName")
        // Skip loading if the module already is installed. Perform success action directly.
        if (splitInstallManager.installedModules.contains(moduleName)) {
            updateProgressMessage("Already installed")
            onSuccessfulLoad(moduleName, launch = true)
            return
        }
        SplitInstallRequest.newBuilder().addModule(moduleName).build().also { splitInstallManager.startInstall(it) }
        updateProgressMessage("Starting install for $moduleName")
    }

    private fun installAllFeaturesNow() {
        SplitInstallRequest.newBuilder()
            .addModule(moduleKotlin)
            .addModule(moduleJava)
            .addModule(moduleNative)
            .addModule(moduleAssets)
            .addModule(moduleHeavy)
            .build()
            .also { request ->
                splitInstallManager.startInstall(request)
                    .addOnSuccessListener { toastAndLog("Loading ${request.moduleNames}") }
                    .addOnFailureListener { toastAndLog("Failed to install all ${request.moduleNames}") }
            }
    }

    private fun installAllFeaturesDeferred() {
        val modules = listOf(moduleKotlin, moduleJava, moduleAssets, moduleNative, moduleHeavy)
        splitInstallManager.deferredInstall(modules)
            .addOnSuccessListener { toastAndLog("Deferred installation of $modules") }
            .addOnFailureListener { toastAndLog("Failed to install all $modules") }
    }

    private fun requestUninstall() {
        toastAndLog("Requesting uninstall of all modules. This will happen at some point in the future.")
        splitInstallManager.installedModules.toList().let { installedModules ->
            splitInstallManager.deferredUninstall(installedModules)
                .addOnSuccessListener { toastAndLog("Uninstalling $installedModules") }
                .addOnFailureListener { toastAndLog("Failed to uninstall $installedModules") }
        }
    }

    private fun toastAndLog(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        Log.d(TAG, text)
    }
}

private const val TAG = "DynamicFeatures"
