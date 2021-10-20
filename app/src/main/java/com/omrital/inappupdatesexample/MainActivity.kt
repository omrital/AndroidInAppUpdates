package com.omrital.inappupdatesexample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability


// Expose to React native:
// 1.  async => checkUpdatesAvailability() => Promise: { flexible: true, immediate: false }
// 2.  startUpdateFlow(updateType: Int, callback: InAppUpdateCallback) => void
// 3.  restartApp() => void


class UpdatesAvailabilityInfo(var flexible: Boolean = false, var immediate: Boolean = false)

enum class CancelReason {
    UPDATE_TYPE_NOT_ALLOWED,
    UPDATE_NOT_AVAILABLE,
    UPDATE_REJECTED_BY_USER,
    UPDATE_FAILED
}

interface InAppUpdateCallback {
    fun onCancel(reason: CancelReason)
    fun onDownloading(bytesDownloaded: Long, totalBytesToDownload: Long)
    fun onUpdateCompleted()
}

class MainActivity : AppCompatActivity() {

    private var appUpdateManager: AppUpdateManager? = null;
    private var callback: InAppUpdateCallback? = null;
    private val IN_APPP_UPDATES_REQUEST_CODE = 13

    private val installStatusListener: InstallStateUpdatedListener = InstallStateUpdatedListener { installState ->
        when(installState.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = installState.bytesDownloaded()
                val totalBytesToDownload = installState.totalBytesToDownload()
                callback?.onDownloading(bytesDownloaded, totalBytesToDownload) // update progress bar
            }
            InstallStatus.DOWNLOADED -> {
                this.onUpdateCompleted()
                callback?.onUpdateCompleted() // ask the user restart the app
            }
            else -> { }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        appUpdateManager = AppUpdateManagerFactory.create(this)
    }

    private fun onUpdateCompleted() {
        appUpdateManager?.unregisterListener(installStatusListener)
    }

    // restartApp() => void
    private fun restartApp() {
        // After user confirm - force restart the app
        appUpdateManager!!.completeUpdate()
    }

    // async => checkUpdatesAvailability() => Promise: { flexible: true, immediate: false }
    private fun checkUpdatesAvailability(): UpdatesAvailabilityInfo {
        val availabilityInfo = UpdatesAvailabilityInfo(
            flexible = false,
            immediate = false
        )
        val appUpdateInfoTask = appUpdateManager?.appUpdateInfo

        appUpdateInfoTask?.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                availabilityInfo.flexible = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                availabilityInfo.immediate = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            }
        }
        return availabilityInfo;      // resolve the promise
    }

    // startUpdateFlow(updateType: Int, callback: InAppUpdateCallback) => void
    private fun startUpdateFlow(updateType: Int, callback: InAppUpdateCallback) {
        this.callback = callback

        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                if (!appUpdateInfo.isUpdateTypeAllowed(updateType)) {
                    callback.onCancel(CancelReason.UPDATE_TYPE_NOT_ALLOWED)
                } else {
                    startUpdateFlowForResult(appUpdateInfo, updateType)
                }
            } else {
                 callback.onCancel(CancelReason.UPDATE_NOT_AVAILABLE)
            }
        }
    }

    private fun startUpdateFlowForResult(appUpdateInfo: AppUpdateInfo, updateType: Int) {
        if (updateType == AppUpdateType.FLEXIBLE) { // Monitoring the update state is required for only flexible downloads
            appUpdateManager?.registerListener(installStatusListener)
        }
        appUpdateManager?.startUpdateFlowForResult(
            // Pass the intent that is returned by 'getAppUpdateInfo()'.
            appUpdateInfo,
            // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
            updateType, // AppUpdateType.FLEXIBLE | AppUpdateType.IMMEDIATE
            // The current activity making the update request.
            this,
            // Include a request code to later monitor this update request.
            IN_APPP_UPDATES_REQUEST_CODE
        )
    }

    override fun onResume() {
        super.onResume()
        this.resumeImmediateUpdateIfNeeded()
    }

    private fun resumeImmediateUpdateIfNeeded() {
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // If an in-app update is already running, resume the update.
                appUpdateManager?.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    this,
                    IN_APPP_UPDATES_REQUEST_CODE
                );
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IN_APPP_UPDATES_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    //  user approved the update by the update dialog - do nothing
                }
                Activity.RESULT_CANCELED -> {
                    callback?.onCancel(CancelReason.UPDATE_REJECTED_BY_USER) // user canceled the update by the update dialog
                }
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                    callback?.onCancel(CancelReason.UPDATE_FAILED) // update flow failed
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}