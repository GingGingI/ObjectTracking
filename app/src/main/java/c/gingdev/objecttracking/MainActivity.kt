package c.gingdev.objecttracking

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraDevice
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.concurrent.thread

class MainActivity: AppCompatActivity(),
    TextureView.SurfaceTextureListener,
    Camera2Api.Camera2Interface{

    private val mCamera by lazy {
        Camera2Api(this)
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView.surfaceTextureListener = this
    }

    override fun onResume() {
        super.onResume()

        if (allPermissionsGranted()) {
            if (textureView.isAvailable) {
                openCamera()
            } else {
                textureView.surfaceTextureListener = this
            }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
    }

    private fun openCamera() {
        mCamera.startBackgroundThread()
        val cameraManager = mCamera.cameraManager(this)
        val cameraId = mCamera.cameraCharacteristics(cameraManager)
        mCamera.cameraDevice(cameraManager, cameraId)
    }

    private fun closeCamera() {
        mCamera.stopBackgroundThread()
        mCamera.closeCamera()
    }


    private fun runObjectDetection(bitmap: Bitmap) {
        // Step 1: create MLKit's VisionImage object
        val options = FirebaseVisionObjectDetectorOptions.Builder()
            .setDetectorMode(FirebaseVisionObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()  //Add this if you want to detect multiple objects at once
            .enableClassification()  // Add this if you want to classify the detected objects into categories
            .build()

        val detector = FirebaseVision.getInstance().getOnDeviceObjectDetector(options)

        val image = FirebaseVisionImage.fromBitmap(bitmap)

        // Step 3: feed given image to detector and setup callback
        detector.processImage(image)
            .addOnSuccessListener {
                Log.e("it", it.toString())
                val drawingView = DrawingView(applicationContext, it)
                drawingView.draw(Canvas(bitmap))
                runOnUiThread {
                    imageView.setImageBitmap(bitmap)
                }
            }.addOnFailureListener {
                // Task failed with an exception
                Toast.makeText(
                    baseContext, "Oops, something went wrong!",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

//    Permissions
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                openCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture?, i: Int, i1: Int) {

    }
    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture?) {
        onceInTentime {
            runObjectDetection(textureView.bitmap)
        }
    }
    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture?): Boolean {
        return true
    }
    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?, i: Int, i1: Int) {
        openCamera()
    }

    override fun onCameraDeviceOpened(cameraDevice: CameraDevice, cameraSize: Size) {
        val texture = textureView.surfaceTexture
        texture.setDefaultBufferSize(cameraSize.width, cameraSize.height)
        val surface = Surface(texture)

        mCamera.captureSession(cameraDevice, surface)
        mCamera.captureRequest(cameraDevice, surface)
    }

    private var count: Int = 0
    private fun onceInTentime(func: () -> Unit) {
        if (count++ > 10) {
            count = 0
            func()
        }
    }
}