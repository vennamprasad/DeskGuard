package com.prasoft.deskguard.radar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * Headless front-camera photo capturer for Desk Guard intruder evidence collection.
 * Captures a still JPEG without requiring a visual preview or Activity viewfinder.
 */
class IntruderPhotoCapturer(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var lastCaptureTimestamp = 0L
    private val cooldownMs = 5000L // 5-second anti-spam cooldown

    fun capturePhoto(onCaptured: (String) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTimestamp < cooldownMs) {
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val camManager = cameraManager ?: return
        val frontCameraId = findFrontCameraId(camManager) ?: return

        lastCaptureTimestamp = now

        val handlerThread = HandlerThread("IntruderCaptureThread").apply { start() }
        val backgroundHandler = Handler(handlerThread.looper)

        // Standard 640x480 resolution is fast, lightweight, and stealthy
        val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    val outputDir = File(context.filesDir, "intruder_captures").apply { mkdirs() }
                    val photoFile = File(outputDir, "intruder_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(photoFile).use { fos ->
                        fos.write(bytes)
                    }

                    onCaptured(photoFile.absolutePath)
                } catch (_: Exception) {
                } finally {
                    image.close()
                }
            }
        }, backgroundHandler)

        try {
            camManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val surface = imageReader.surface
                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }

                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        session.capture(
                                            captureBuilder.build(),
                                            object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    result: TotalCaptureResult
                                                ) {
                                                    super.onCaptureCompleted(session, request, result)
                                                    cleanup(camera, session, imageReader, handlerThread)
                                                }
                                            },
                                            backgroundHandler
                                        )
                                    } catch (_: Exception) {
                                        cleanup(camera, session, imageReader, handlerThread)
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    cleanup(camera, session, imageReader, handlerThread)
                                }
                            },
                            backgroundHandler
                        )
                    } catch (_: Exception) {
                        cleanup(camera, null, imageReader, handlerThread)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cleanup(camera, null, imageReader, handlerThread)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cleanup(camera, null, imageReader, handlerThread)
                }
            }, backgroundHandler)
        } catch (_: SecurityException) {
            handlerThread.quitSafely()
        } catch (_: Exception) {
            handlerThread.quitSafely()
        }
    }

    private fun cleanup(
        camera: CameraDevice?,
        session: CameraCaptureSession?,
        imageReader: ImageReader?,
        handlerThread: HandlerThread
    ) {
        try { session?.close() } catch (_: Exception) {}
        try { camera?.close() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { handlerThread.quitSafely() } catch (_: Exception) {}
    }

    private fun findFrontCameraId(manager: CameraManager): String? {
        return try {
            manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_FRONT
            }
        } catch (_: Exception) {
            null
        }
    }
}
