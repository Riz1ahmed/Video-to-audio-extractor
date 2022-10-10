package com.video_lab.permission_controller

interface PermissionListener {
    fun allGranted()
    fun allNotGranted(deniedList: ArrayList<String>){}
}