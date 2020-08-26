package c.gingdev.objecttracking

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraDevice
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Rational
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner

import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import c.gingdev.objecttracking.common.Constants
import c.gingdev.objecttracking.common.DrawView
import c.gingdev.objecttracking.common.GlobalValues
import c.gingdev.objecttracking.common.ImageUtils
import c.gingdev.objecttracking.ml.MLProcessor

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity: AppCompatActivity() {
    private val TAG = "MainActivity"

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private var globals: GlobalValues? = null

    private lateinit var drawView: DrawView

    private lateinit var mlTargetSpinner: Spinner
    private lateinit var mlClassifierSpinner: Spinner
    private var mlClassifier = "Quant ImageNet"
    private var mlTarget = "Full Screen"

    private var facingCameraX = Constants.FACING_CAMERAX

    private var objectDetectAwaitSecond = 200L
    private val imageLabelerAwaidSecond = Constants.IMAGE_LABELER_AWAIT_MILLISECOND

    private lateinit var activeModelName: String
    private lateinit var mlProcessor: MLProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        onRequestPermission()
    }

    private fun initLayout() {
        initializeGlobals()

        drawView = findViewById(R.id.camera_drawview)
        drawView.isFocusable = false

        configureSpinner()

        objectDetectAwaitSecond = globals!!.objectDetector!!.awaitMilliSecond
        mlProcessor = MLProcessor(globals!!)

        cameraTextureView.post { startCameraX() }
        cameraTextureView.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
            updateTransform()
        }
    }

    private fun configureSpinner() {
        mlTargetSpinner = findViewById(R.id.mlTarget)
        val mlTargetAdapter = ArrayAdapter(
            applicationContext,
            android.R.layout.simple_spinner_item,
            Constants.MLTARGET_ARRAY
        )
        mlTargetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mlTargetSpinner.adapter = mlTargetAdapter
        mlTarget = Constants.MLTARGET_FULL_SCREEN
        mlTargetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long) {
                val spinnerParent = parent as Spinner
                mlTarget = spinnerParent.selectedItem as String
                when (mlTarget) {
                    Constants.MLTARGET_Rectangle -> {
                        drawView.makeVisible()
                    }
                    else -> {
                        drawView.makeInvisible()
                    }
                }
                Log.i(TAG, "Selected mlTarget ${mlTarget}")
                cameraTextureView.post { startCameraX() }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                mlTarget = Constants.MLTARGET_FULL_SCREEN
            }
        }

        mlClassifierSpinner = findViewById(R.id.mlClassifier)
        val mlClassifierAdapter = ArrayAdapter(
            applicationContext,
            android.R.layout.simple_spinner_item,
            Constants.MLCLASSIFIER_ARRAY
        )
        mlClassifierAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mlClassifierSpinner.adapter = mlClassifierAdapter
        mlClassifier = Constants.MLCLASSIFIER_QUANT_IMAGENET
        mlClassifierSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                val spinnerParent = parent as Spinner
                mlClassifier = spinnerParent.selectedItem as String
                updateActiveModelName()
                Log.i(TAG, "Selected mlClassifier ${mlClassifier}")
                cameraTextureView.post { startCameraX() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                mlClassifier = Constants.MLCLASSIFIER_QUANT_IMAGENET
            }
        }
        updateActiveModelName()
    }

    private fun initializeGlobals() {
        if (globals == null){
            globals = application as GlobalValues
            globals!!.initialize(this)
        }
    }

    private fun startCameraX() {
        CameraX.unbindAll()

        val screenSize = Size(cameraTextureView.width, cameraTextureView.height)
        val screenAspectRatio = Rational(16,9)

        val previewConfig = PreviewConfig.Builder()
            .apply {
                setLensFacing(facingCameraX)
                setTargetResolution(screenSize)
                setTargetAspectRatio(screenAspectRatio)
                setTargetRotation(windowManager.defaultDisplay.rotation)
                setTargetRotation(cameraTextureView.display.rotation)
            }.build()

        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            val parent = cameraTextureView.parent as ViewGroup
            parent.removeView(cameraTextureView)
            cameraTextureView.surfaceTexture = it.surfaceTexture
            parent.addView(cameraTextureView, 0)
            updateTransform()
        }

        val analyzerConfig = buildAnalyzerConfig()
        val imageAnalysis = ImageAnalysis(analyzerConfig)
        imageAnalysis.analyzer = ImageAnalysis.Analyzer { image: ImageProxy, rotationDegrees: Int ->
            mlImageAnalysis(image, rotationDegrees)
        }

        CameraX.bindToLifecycle(this, preview, imageAnalysis)
    }

    private fun mlImageAnalysis(image: ImageProxy, rotationDegrees: Int) {
        when (mlTarget){
            Constants.MLTARGET_FULL_SCREEN -> {
                overlay.setConfiguration(400,
                    380,
                    Color.TRANSPARENT)
                when (activeModelName) {
                    Constants.IMAGE_CLASSIFIER.MOBILENETV2_IMAGE_CLASSIFIER.modelName,
                    Constants.IMAGE_CLASSIFIER.MNASNET_IMAGE_CLASSIFIER.modelName -> {
                        mlProcessor.classifyAwait(
                            image,
                            overlay,
                            activeModelName)
                    }
                    Constants.MLCLASSIFIER_IMAGE_LABELER -> {
                        mlProcessor.labelImageAwait(
                            image,
                            rotationDegrees,
                            overlay,
                            imageLabelerAwaidSecond)
                    }
                    else -> {
                        Log.e(TAG, "Wrong configuration for mlClassifier")
                    }
                }
            }
            Constants.MLTARGET_OBJECT_DETECTION -> {
                when (activeModelName) {
                    Constants.IMAGE_CLASSIFIER.MOBILENETV2_IMAGE_CLASSIFIER.modelName,
                    Constants.IMAGE_CLASSIFIER.MNASNET_IMAGE_CLASSIFIER.modelName -> {
                        mlProcessor.classifyFromDetectionAwait(
                            image,
                            rotationDegrees,
                            overlay,
                            activeModelName,
                            objectDetectAwaitSecond)
                    }
                    Constants.MLCLASSIFIER_IMAGE_LABELER -> {
                        mlProcessor.labelImageFromDetectionAwait(
                            image,
                            rotationDegrees,
                            overlay,
                            objectDetectAwaitSecond,
                            imageLabelerAwaidSecond)
                    }
                    else -> {
                        Log.e(TAG, "Wrong configuration for mlClassifier")
                    }
                }
            }
            Constants.MLTARGET_Rectangle -> {
                val bitmap = ImageUtils.imageToBitmap(image)
                val croppedImage: Bitmap =
                    if (drawView.drawId == null) bitmap
                    else ImageUtils.cropImageFromPoints(bitmap, drawView.points)

                when (activeModelName) {
                    Constants.IMAGE_CLASSIFIER.MOBILENETV2_IMAGE_CLASSIFIER.modelName,
                    Constants.IMAGE_CLASSIFIER.MNASNET_IMAGE_CLASSIFIER.modelName -> {
                        mlProcessor.classifyAwait(
                            croppedImage!!,
                            overlay,
                            activeModelName)
                    }
                    Constants.MLCLASSIFIER_IMAGE_LABELER -> {
                        mlProcessor.labelImageAwait(
                            croppedImage!!,
                            rotationDegrees,
                            overlay,
                            imageLabelerAwaidSecond)
                    }
                    else -> {
                        Log.e(TAG, "Wrong configuration for mlClassifier")
                    }
                }
            }
            else -> {
                Log.e(TAG, "Wrong configuration for mlTarget")
            }
        }
    }

    private fun updateTransform() {
        val matrix = Matrix()
        val centerX = cameraTextureView.width / 2f
        val centerY = cameraTextureView.height / 2f

        val rotationDegrees = when (cameraTextureView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        cameraTextureView.setTransform(matrix)
    }

    private var mlThread = HandlerThread("MLThread")
    private fun buildAnalyzerConfig(): ImageAnalysisConfig {
        return ImageAnalysisConfig.Builder().apply {
            mlThread = HandlerThread("MLThread").apply {
                start()
            }
            setCallbackHandler(Handler(mlThread.looper))
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()
    }


    private fun updateActiveModelName(){
        activeModelName = when(mlClassifier){
            "Quant ImageNet" -> Constants.IMAGE_CLASSIFIER.MOBILENETV2_IMAGE_CLASSIFIER.modelName
            "Float ImageNet" -> Constants.IMAGE_CLASSIFIER.MNASNET_IMAGE_CLASSIFIER.modelName
            "Image Labeler" -> "Image Labeler"
            else -> "Image Labeler"
        }
    }


    //    Permissions
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            onRequestPermission()
        }
    }

    private fun onRequestPermission() {
        if (allPermissionsGranted()) {
            initLayout()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private var count: Int = 0
    private fun onceInTentime(func: () -> Unit) {
        if (count++ > 10) {
            count = 0
            func()
        }
    }
}
