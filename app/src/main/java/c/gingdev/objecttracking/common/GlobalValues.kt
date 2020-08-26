package c.gingdev.objecttracking.common

import android.app.Application
import android.content.Context
import android.util.Log
import c.gingdev.objecttracking.ml.ImageClassifier
import c.gingdev.objecttracking.ml.ImageLabeler
import c.gingdev.objecttracking.ml.ObjectDetector

class GlobalValues: Application() {
    val TAG = "Globals"

    var imageClassifierMap: MutableMap<String, ImageClassifier> = mutableMapOf()
    var imageLabeler: ImageLabeler? = null
    var objectDetector: ObjectDetector? = null
    var initialized = false

    override fun onCreate() {
        super.onCreate()
    }

    fun initialize(context: Context){
        initializeAllImageClassifier(context)

        initializeObjectDetector(
            Constants.OBJECT_DETECT.SINGLE_STREAM_OBJECT_DETECTOR)

        initializeImageLabeler(
            Constants.IMAGE_LABELER_MODE,
            Constants.IMAGE_LABELER_CONFIDENCE_THRESHOLD,
            Constants.IMAGE_LABELER_AWAIT_MILLISECOND)
        initialized = true
    }

    fun initializeAllImageClassifier(context: Context){
        initializeImageClassifier(context,
            Constants.IMAGE_CLASSIFIER.MOBILENETV2_IMAGE_CLASSIFIER)
        initializeImageClassifier(context,
            Constants.IMAGE_CLASSIFIER.MNASNET_IMAGE_CLASSIFIER)
    }

    fun initializeImageClassifier(context: Context,
                                  imageClassifier: Constants.IMAGE_CLASSIFIER){
        if (!imageClassifierMap.containsKey(imageClassifier.modelName)){
            Log.i(TAG, "Loading ${imageClassifier.modelName}")
            imageClassifierMap[imageClassifier.modelName] = ImageClassifier(
                context,
                imageClassifier.modelName,
                imageClassifier.imagenetLabelPath,
                imageClassifier.numOfBytesPerChannel,
                imageClassifier.dimBatchSize,
                imageClassifier.dimPixelSize,
                imageClassifier.dimImgSize,
                imageClassifier.quantized,
                imageClassifier.resultsToShow,
                imageClassifier.awaitMillisecond,
                imageClassifier.imageMean,
                imageClassifier.imageStd
            )
        }
        else{
            Log.i(TAG, "Using ${imageClassifier.modelName}")
        }
    }

    fun initializeObjectDetector(objectDetect: Constants.OBJECT_DETECT){
        if (objectDetector == null){
            Log.i(TAG, "Loading object detector")
            objectDetector = ObjectDetector(
                objectDetect.objectDetectName,
                objectDetect.objectDetectMode,
                objectDetect.objectDetectClassify,
                objectDetect.objectDetectMultiple,
                objectDetect.awaitMillisecond)
        }
        else{
            Log.i(TAG, "Using object detector")
        }
    }

    fun initializeImageLabeler(mode: String,
                               confidenceThreshold:Float,
                               awaitMilliSeconds: Long){
        if (imageLabeler == null){
            Log.i(TAG, "Loading image labeler")
            imageLabeler = ImageLabeler(
                mode,
                confidenceThreshold,
                awaitMilliSeconds)
        }
        else{
            Log.i(TAG, "Using image labeler")
        }
    }

    fun closeAll(){
        objectDetector!!.close()
        imageLabeler!!.close()
        for ((_,v) in imageClassifierMap){
            v.close()
        }
    }
    override fun onTerminate() {
        super.onTerminate()
        closeAll()
    }

}