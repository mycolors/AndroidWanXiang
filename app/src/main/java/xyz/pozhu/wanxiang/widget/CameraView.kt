package xyz.pozhu.wanxiang.widget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraView : FrameLayout {

    constructor(context:Context) : this(context,null)

    constructor(context: Context,attrs: AttributeSet?) : this(context,attrs,0)


    constructor(  context: Context,
                  attrs: AttributeSet?,
                  defStyleAttr: Int):super(context,attrs,defStyleAttr,0)



    // 核心组件
    private val previewView: PreviewView = PreviewView(context)
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: Recorder? = null
    private var recording: Recording? = null

    // 线程池
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // 状态监听
    var onCaptureSuccess: ((File) -> Unit)? = null
    var onVideoRecordStart: (() -> Unit)? = null
    var onVideoRecordEnd: ((File) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    // 配置参数
    var lensFacing = CameraSelector.LENS_FACING_BACK
        set(value) {
            field = value
            restartCamera()
        }

    init {
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT)
        addView(previewView,layoutParams)
    }

    /**
     * 启动相机预览
     */
    @SuppressLint("MissingPermission")
    fun startCamera(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(lifecycleOwner)
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 绑定相机用例
     */
    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner) {
        val cameraProvider = cameraProvider ?: return

        // 相机选择器
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        // 预览用例
        val preview = Preview.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // 拍照用例
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(previewView.display.rotation)
            .build()

        // 视频录制用例
        videoCapture = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture?.let { VideoCapture.withOutput(it) }
            )
        } catch (exc: Exception) {
            onError?.invoke(exc)
        }
    }

    /**
     * 拍照
     */
    fun takePicture(outputDirectory: File) {
        val imageCapture = imageCapture ?: return

        // 创建输出文件
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        // 创建输出选项
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    post{
                        onCaptureSuccess?.invoke(photoFile)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    post{
                        onError?.invoke(exc)
                    }
                }
            }
        )
    }

    /**
     * 开始录制视频
     */
    fun startRecording(outputDirectory: File) {
        val videoCapture = videoCapture ?: return
        if (recording != null) return

        // 创建输出文件
        val videoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".mp4"
        )

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = videoCapture.prepareRecording(context, outputOptions)
            .start(cameraExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> post {   onVideoRecordStart?.invoke()}
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            post {   onVideoRecordEnd?.invoke(videoFile) }
                        }
                        recording = null
                    }
                }
            }
    }

    /**
     * 停止录制
     */
    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    /**
     * 切换摄像头
     */
    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    /**
     * 重启相机
     */
    private fun restartCamera() {
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
        videoCapture = null
    }

    /**
     * 释放资源
     */
    fun release() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

}