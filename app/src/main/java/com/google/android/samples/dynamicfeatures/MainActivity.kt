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

private const val packageName = "com.google.android.samples.dynamicfeatures.ondemand"
private const val kotlinSampleClassname = "$packageName.KotlinSampleActivity"
private const val javaSampleClassname = "$packageName.JavaSampleActivity"
private const val nativeSampleClassname = "$packageName.NativeSampleActivity"

/** Activity that displays buttons and handles loading of feature modules. */
class MainActivity : AppCompatActivity() {

    private val splitInstallManager by lazy { SplitInstallManagerFactory.create(this) }
    private val moduleAssets by lazy { getString(R.string.module_assets) }

    private lateinit var progress: Group
    private lateinit var buttons: Group
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private val clickListener by lazy {
        View.OnClickListener {
            when (it.id) {
                R.id.btn_load_kotlin -> launchActivity(kotlinSampleClassname)
                R.id.btn_load_java -> launchActivity(javaSampleClassname)
                R.id.btn_load_native -> launchActivity(nativeSampleClassname)
                R.id.btn_load_assets -> onAssetsRequested()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeViews()
    }

    /** Display assets loaded from the assets feature module. */
    private fun onAssetsRequested() {
        if (splitInstallManager.installedModules.contains(moduleAssets)) {
            displayButtons()
            displayAssets()
        } else {
            toastAndLog("Assets module is not installed")
            requestAssetsInstall()
        }
    }

    private fun displayAssets() {
        // Get the asset manager with a refreshed context, to access content of newly installed apk.
        val assetManager = createPackageContext(packageName, 0).assets
        // Now treat it like any other asset file.
        val assets = assetManager.open("assets.txt")
        val assetContent = assets.bufferedReader().use { it.readText() }
        AlertDialog.Builder(this).setTitle("Asset content").setMessage(assetContent).show()
    }

    private fun requestAssetsInstall() {
        updateProgressMessage("Starting install for assets module")
        SplitInstallRequest.newBuilder().addModule(moduleAssets).build().also {
            splitInstallManager.startInstall(it)
                .addOnCompleteListener { displayAssets() }
                .addOnSuccessListener { toastAndLog("Assets module install started") }
                .addOnFailureListener { displayButtons() }
        }
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
}

fun MainActivity.toastAndLog(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    Log.d(TAG, text)
}

private const val TAG = "DynamicFeatures"
