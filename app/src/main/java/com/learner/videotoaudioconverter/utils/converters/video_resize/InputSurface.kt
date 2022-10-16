package com.learner.videotoaudioconverter.utils.converters.video_resize

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.view.Surface

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Holds state associated with a Surface used for MediaCodec encoder input.
 *
 *
 * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses that
 * to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to be sent
 * to the video encoder.
 */
internal class InputSurface(surface: Surface?) {
    private var mEGLDisplay = EGL14.EGL_NO_DISPLAY
    private var mEGLContext = EGL14.EGL_NO_CONTEXT
    private var mEGLSurface = EGL14.EGL_NO_SURFACE

    /**
     * Returns the Surface that the MediaCodec receives buffers from.
     */
    var surface: Surface?
        private set

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
     */
    private fun eglSetup() {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEGLDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }
        // Configure EGL for recordable and OpenGL ES 2.0.  We want enough RGB bits
        // to minimize artifacts from possible YUV conversion.
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                mEGLDisplay, attribList, 0, configs, 0, configs.size,
                numConfigs, 0
            )
        ) {
            throw RuntimeException("unable to find RGB888+recordable ES2 EGL config")
        }
        // Configure context for OpenGL ES 2.0.
        val attrib_list = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        mEGLContext = EGL14.eglCreateContext(
            mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
            attrib_list, 0
        )
        checkEglError("eglCreateContext")
        if (mEGLContext == null) {
            throw RuntimeException("null context")
        }
        // Create a window surface, and attach it to the Surface we received.
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_NONE
        )
        mEGLSurface = EGL14.eglCreateWindowSurface(
            mEGLDisplay, configs[0], surface,
            surfaceAttribs, 0
        )
        checkEglError("eglCreateWindowSurface")
        if (mEGLSurface == null) {
            throw RuntimeException("surface was null")
        }
    }

    /**
     * Discard all resources held by this class, notably the EGL context.  Also releases the
     * Surface that was passed to our constructor.
     */
    fun release() {
        if (mEGLDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface)
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(mEGLDisplay)
        }
        surface!!.release()
        mEGLDisplay = EGL14.EGL_NO_DISPLAY
        mEGLContext = EGL14.EGL_NO_CONTEXT
        mEGLSurface = EGL14.EGL_NO_SURFACE
        surface = null
    }

    /**
     * Makes our EGL context and surface current.
     */
    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun makeUnCurrent() {
        if (!EGL14.eglMakeCurrent(
                mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
        ) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     */
    fun swapBuffers(): Boolean {
        //println("swapBuffers")
        return EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
    }

    /**
     * Queries the surface's width.
     */
    val width: Int
        get() {
            val value = IntArray(1)
            EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_WIDTH, value, 0)
            return value[0]
        }

    /**
     * Queries the surface's height.
     */
    val height: Int
        get() {
            val value = IntArray(1)
            EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_HEIGHT, value, 0)
            return value[0]
        }

    /**
     * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
     */
    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs)
    }

    /**
     * Checks for EGL errors.
     */
    private fun checkEglError(msg: String) {
        var error: Int
        if (EGL14.eglGetError().also { error = it } != EGL14.EGL_SUCCESS) {
            throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
        }
    }

    companion object {
        private const val TAG = "InputSurface"
        private const val VERBOSE = false
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }

    /**
     * Creates an InputSurface from a Surface.
     */
    init {
        if (surface == null) {
            throw NullPointerException()
        }
        this.surface = surface
        eglSetup()
    }
}