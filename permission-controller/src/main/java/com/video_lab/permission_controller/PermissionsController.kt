package com.video_lab.permission_controller

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * @author Riz1Ahmed
 * @since 07/04/2021
 * you can get permissions of user device very easily by using
 * this module. Just get an Instance of this Object and call
 * the 'check' method using the object
 */

object PermissionsController {
    /**
     * @param context Activity Context
     * @param perms Require permission list from Manifest.android.
     * @param listener For override the functions.
     *
     * Most of the time the 'allNotGranted' is not require. So if you need it
     * you need to override manually.
     * Ex for storage perm: android.Manifest.permission.WRITE_EXTERNAL_STORAGE
     */
    fun check(context: Context, perms: ArrayList<String>, listener: PermissionListener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) listener.allGranted()
        else {
            perms.forEach {
                if (context.checkSelfPermission(it) == PackageManager.PERMISSION_DENIED)
                    PermissionCheckerActivity.deniedList.add(it)
            }
            if (PermissionCheckerActivity.deniedList.isNotEmpty()) {
                PermissionCheckerActivity.listener = listener
                context.startActivity(Intent(context, PermissionCheckerActivity::class.java))
            } else listener.allGranted()
        }
    }
}