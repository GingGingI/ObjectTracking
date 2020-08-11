package c.gingdev.objecttracking

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import java.util.*

class Camera2Api(private val camera2Interface: Camera2Interface) {
    private var mCameraSize: Size? = null

    private var mCaptureSession: CameraCaptureSession? = null
    private var mCameraDevice: CameraDevice? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null

    fun cameraManager(activity: Activity): CameraManager{
        return activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    fun cameraCharacteristics(cameraManager: CameraManager): String? {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                if (characteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK) {
                    val map = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]

                    val sizes: Array<Size>? = map?.getOutputSizes(SurfaceTexture::class.java)
                    if (sizes != null) {
                        for (size in sizes) {
                            if (size.width > mCameraSize?.width ?: 0) {
                                mCameraSize = size
                            }
                        }
                    }
                    return cameraId
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        return null
    }

    @SuppressLint("MissingPermission")
    fun cameraDevice(cameraManager: CameraManager, cameraId: String?) {
        try {
            cameraId?.run { cameraManager.openCamera(this, mCameraDeviceStateCallBack, null) }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun captureSession(cameraDevice: CameraDevice, surface: Surface) {
        try {
            cameraDevice.createCaptureSession(Collections.singletonList(surface), mCaptureSessionCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun captureRequest(cameraDevice: CameraDevice, surface: Surface) {
        try {
            mPreviewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(surface)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val mCameraDeviceStateCallBack: CameraDevice.StateCallback = object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            camera2Interface.onCameraDeviceOpened(camera, mCameraSize!!)
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
        }
    }
    private val mCaptureSessionCallback: CameraCaptureSession.StateCallback = object: CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            try {
                mCaptureSession = cameraCaptureSession
                mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                cameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder!!.build(), mCaptureCallback, mBackgroundHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {}
    }
    private val mCaptureCallback: CameraCaptureSession.CaptureCallback = object: CameraCaptureSession.CaptureCallback() {}

    fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun closeCamera() {
        if (mCaptureSession != null) {
            mCaptureSession!!.close()
            mCaptureSession = null
        }

        if (mCameraDevice != null) {
            mCameraDevice!!.close()
            mCameraDevice = null
        }
    }

    interface Camera2Interface {
        fun onCameraDeviceOpened(cameraDevice: CameraDevice, cameraSize: Size)
    }
}