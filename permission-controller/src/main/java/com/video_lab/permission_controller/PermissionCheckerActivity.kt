package com.video_lab.permission_controller

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.M)
class PermissionCheckerActivity : Activity() {
    companion object {
        var listener: PermissionListener? = null
        var deniedList = arrayListOf<String>()
        private const val PERMISSION_CODE = 1123
        private const val OPEN_SETTING_CODE = 1122
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(deniedList.toTypedArray(), PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        deniedList.clear()
        var notAskAgainOrBlocked = false

        for (i in permissions.indices) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                deniedList.add(permissions[i])
                if (!shouldShowRequestPermissionRationale(permissions[i]))
                    notAskAgainOrBlocked = true
            }
        }
        if (deniedList.isEmpty()) granted()
        else denied(notAskAgainOrBlocked)
    }

    private fun granted() {
        listener?.allGranted()
        finish()
    }

    private fun denied(notAskAgainOrBlocked: Boolean) {
        listener?.allNotGranted(deniedList)
        deniedList.clear()
        if (notAskAgainOrBlocked) openSettingsPermsScreen()
        else finish()
        //Log.d("xyz", "notAskAgain or blocked=$notAskAgainOrBlocked")
    }

    private fun openSettingsPermsScreen() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Required permission(s) have been cancelled! Please provide them from settings.")
            .setPositiveButton("OPEN SETTINGS") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
                startActivityForResult(intent, OPEN_SETTING_CODE)
                finish()
            }
            .setNegativeButton("CANCEL") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create().show()
    }

    override fun onDestroy() {
        listener = null
        deniedList.clear()
        super.onDestroy()
    }
}