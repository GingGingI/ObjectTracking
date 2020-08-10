package c.gingdev.objecttracking

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity: AppCompatActivity(),
	ActivityCompat.OnRequestPermissionsResultCallback {

//	Camera Request Code
	companion object {
		const val ODT_PERMISSIONS_REQUEST: Int = 1
		const val ODT_REQUEST_IMAGE_CAPTURE = 1
	}

	private lateinit var imageBitmap: Bitmap

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		fab.setOnClickListener {
//			Toast.makeText(applicationContext, "click", Toast.LENGTH_LONG).show()
//
//			val takePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//			if (takePhotoIntent.resolveActivity(packageManager) != null) {
//				val values = ContentValues()
//				values.put(MediaStore.Images.Media.TITLE, "MLKit_codelab")
//				outputFileUri = contentResolver
//					.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
//
//				takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri)
//				startActivityForResult(takePhotoIntent, ODT_REQUEST_IMAGE_CAPTURE)
//			}

			Glide.with(this)
				.asBitmap()
				.load("https://hips.hearstapps.com/hmg-prod.s3.amazonaws.com/images/white-female-shoes-on-feet-royalty-free-image-912581410-1563805834.jpg")
				.into(object: CustomTarget<Bitmap>() {
					override fun onLoadCleared(placeholder: Drawable?) {}

					override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
						imageView.setImageBitmap(resource)
						imageBitmap = resource

//						val image = getCapturedImage()
//
//						// display capture image
//						imageView.setImageBitmap(image)

						// run through ODT and display result
						runObjectDetection(resource)
					}
				})
		}

		if (ActivityCompat.checkSelfPermission(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
			PackageManager.PERMISSION_GRANTED) {

			fab.isEnabled = false
				ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
				ODT_PERMISSIONS_REQUEST
			)
		}
	}

	private val options = FirebaseVisionObjectDetectorOptions.Builder()
		.setDetectorMode(FirebaseVisionObjectDetectorOptions.SINGLE_IMAGE_MODE)
		.enableMultipleObjects()
		.enableClassification()
		.build()
	private val detector = FirebaseVision.getInstance().getOnDeviceObjectDetector(options)

	private fun runObjectDetection(bitmap: Bitmap) {
		// Step 1: create MLKit's VisionImage object
		val image = FirebaseVisionImage.fromBitmap(bitmap)

		// Step 3: feed given image to detector and setup callback
		detector.processImage(image)
			.addOnSuccessListener {
				// Task completed successfully
				// Post-detection processing : draw result
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

	private fun getCapturedImage(): Bitmap {
		val srcImage = imageBitmap

		// crop image to match imageView's aspect ratio
		val scaleFactor = Math.min(
			srcImage.width / imageView.width.toFloat(),
			srcImage.height / imageView.height.toFloat()
		)

		val deltaWidth = (srcImage.width - imageView.width * scaleFactor).toInt()
		val deltaHeight = (srcImage.height - imageView.height * scaleFactor).toInt()

		val scaledImage = Bitmap.createBitmap(
			srcImage, deltaWidth / 2, deltaHeight / 2,
			srcImage.width - deltaWidth, srcImage.height - deltaHeight
		)
		srcImage.recycle()
		return scaledImage
	}
}