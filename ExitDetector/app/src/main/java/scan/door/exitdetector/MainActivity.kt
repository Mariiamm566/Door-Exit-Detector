package scan.door.exitdetector

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var objectDetector: com.google.mlkit.vision.objects.ObjectDetector
    private lateinit var overlayView: OverlayView
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisInProgress = false
    private lateinit var tts: TextToSpeech
    private var isSpeaking = false
    private var lastSpokenGuidance = ""
    private var lastSpokenTime = 0L
    private val guidanceCooldown = 20000L
    private var frameCounter = 0
    private val frameSkip = 2

    companion object {
        private const val TAG = "DoorNavigation"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
        private const val MIN_OBSTACLE_CONFIDENCE = 0.5f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })

        overlayView = findViewById(R.id.overlayView)
        setupOverlayStyle()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeDetection()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupOverlayStyle() {
        val boxPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textBgPaint = Paint().apply {
            color = "#CC000000".toColorInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        overlayView.setPaints(boxPaint, textPaint, textBgPaint)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
        }
    }

    private fun speakGuidance(message: String) {
        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastSpokenTime > guidanceCooldown || message != lastSpokenGuidance) && !isSpeaking) {
            isSpeaking = true
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
            lastSpokenGuidance = message
            lastSpokenTime = currentTime
            Log.d(TAG, "Speaking: $message")
        }
    }

    private fun initializeDetection() {
        setupCustomObjectDetector()
        startCamera()
    }

    private fun setupCustomObjectDetector() {
        try {
            val localModel = LocalModel.Builder()
                .setAssetFilePath("object_detection.tflite")
                .build()

            val customOptions = CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .setClassificationConfidenceThreshold(0.7f)
                .setMaxPerObjectLabelCount(5)
                .build()

            objectDetector = ObjectDetection.getClient(customOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Detector setup error", e)
            runOnUiThread {
                Toast.makeText(this, "Detector initialization failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetResolution(Size(TARGET_WIDTH, TARGET_HEIGHT))
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(TARGET_WIDTH, TARGET_HEIGHT))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    frameCounter++
                    if (frameCounter > frameSkip) {
                        frameCounter = 0
                        if (!analysisInProgress && !isSpeaking) {
                            analysisInProgress = true
                            processImageProxy(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    } else {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider?.unbindAll()

                val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
                preview.setSurfaceProvider(previewView.surfaceProvider)

                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

            } catch (e: Exception) {
                Log.e(TAG, "Camera setup failed", e)
                runOnUiThread {
                    Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            analysisInProgress = false
            return
        }

        try {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    val doorObjects = detectedObjects.filter { obj ->
                        obj.labels.any { label ->
                            label.text.equals("door", ignoreCase = true) &&
                                    label.confidence >= MIN_OBSTACLE_CONFIDENCE
                        }
                    }

                    val obstacles = detectedObjects.filter { obj ->
                        obj.labels.any { label ->
                            !label.text.equals("door", ignoreCase = true) &&
                                    label.confidence >= MIN_OBSTACLE_CONFIDENCE &&
                                    isObstacleInPath(obj, doorObjects, imageProxy.width, imageProxy.height)
                        }
                    }

                    runOnUiThread {
                        overlayView.setDetectedObjects(detectedObjects, imageProxy.width, imageProxy.height)
                        overlayView.invalidate()
                        provideNavigationGuidance(doorObjects, obstacles)


                        overlayView.postDelayed({ overlayView.invalidate() }, 1000)
                    }
                    analysisInProgress = false
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Detection failed", e)
                    analysisInProgress = false
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Image processing error", e)
            imageProxy.close()
            analysisInProgress = false
        }
    }

    private fun isObstacleInPath(
        obj: DetectedObject,
        doors: List<DetectedObject>,
        imageWidth: Int,
        imageHeight: Int
    ): Boolean {
        if (doors.isEmpty()) return false

        val door = doors[0]
        val pathThreshold = imageHeight * 0.6f

        return obj.boundingBox.bottom > pathThreshold &&
                obj.boundingBox.top < door.boundingBox.bottom &&
                boxesOverlapHorizontally(obj.boundingBox, door.boundingBox)
    }

    private fun boxesOverlapHorizontally(box1: Rect, box2: Rect): Boolean {
        return box1.right > box2.left && box1.left < box2.right
    }

    private fun provideNavigationGuidance(doors: List<DetectedObject>, obstacles: List<DetectedObject>) {
        if (doors.isEmpty()) {
            speakGuidance("Scanning for doors. Please move slowly.")
            return
        }

        val door = doors[0]
        val doorLabel = door.labels.firstOrNull()?.text ?: "door"
        val doorConfidence = (door.labels.firstOrNull()?.confidence ?: 0f) * 100

        if (obstacles.isEmpty()) {
            speakGuidance("Walk straight. $doorLabel is ahead with ${doorConfidence.toInt()}% confidence.")
        } else {
            val obstacle = obstacles[0]
            val obstacleName = obstacle.labels.firstOrNull()?.text ?: "object"
            val obstacleConfidence = (obstacle.labels.firstOrNull()?.confidence ?: 0f) * 100

            val doorCenterX = door.boundingBox.centerX()
            val obstacleCenterX = obstacle.boundingBox.centerX()

            val guidance = if (obstacleCenterX < doorCenterX) {
                "Caution: $obstacleName detected on your left with ${obstacleConfidence.toInt()}% confidence. Move right to avoid and reach the $doorLabel."
            } else {
                "Caution: $obstacleName detected on your right with ${obstacleConfidence.toInt()}% confidence. Move left to avoid and reach the $doorLabel."
            }

            speakGuidance(guidance)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        tts.stop()
        tts.shutdown()
    }
}
class OverlayView(context: android.content.Context, attrs: android.util.AttributeSet?) :
    View(context, attrs) {

    private var detectedObjects: List<DetectedObject> = emptyList()
    private lateinit var boxPaint: Paint
    private lateinit var textPaint: Paint
    private lateinit var textBgPaint: Paint
    private var imageWidth = 1
    private var imageHeight = 1

    fun setPaints(boxPaint: Paint, textPaint: Paint, textBgPaint: Paint) {
        this.boxPaint = boxPaint
        this.textPaint = textPaint
        this.textBgPaint = textBgPaint
    }

    fun setDetectedObjects(objects: List<DetectedObject>, imgWidth: Int, imgHeight: Int) {
        detectedObjects = objects
        imageWidth = imgWidth
        imageHeight = imgHeight
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (imageWidth == 0 || imageHeight == 0) return

        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        detectedObjects.forEach { obj ->
            obj.labels.firstOrNull()?.let { label ->
                val left = obj.boundingBox.left * scaleX
                val top = obj.boundingBox.top * scaleY
                val right = obj.boundingBox.right * scaleX
                val bottom = obj.boundingBox.bottom * scaleY

                val scaledRect = RectF(left, top, right, bottom)

                canvas.drawRect(scaledRect, boxPaint)

                val text = "${label.text} ${(label.confidence * 100).toInt()}%"
                val textWidth = textPaint.measureText(text)
                val textHeight = textPaint.textSize

                val textX = scaledRect.left
                val textY = scaledRect.top - 5f

                canvas.drawRect(
                    textX - 5f,
                    textY - textHeight - 5f,
                    textX + textWidth + 5f,
                    textY + 5f,
                    textBgPaint
                )

                canvas.drawText(text, textX, textY, textPaint)
            }
        }
    }
}