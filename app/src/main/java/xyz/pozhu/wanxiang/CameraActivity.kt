package xyz.pozhu.wanxiang

import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import xyz.pozhu.common.viewbinding.viewBinding
import xyz.pozhu.wanxiang.databinding.ActivityCameraBinding
import java.io.File

class CameraActivity : AppCompatActivity() {
     private val viewbinding :ActivityCameraBinding by viewBinding()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(viewbinding.root)
        viewbinding.cameraView.startCamera(this)
        viewbinding.cameraView.onVideoRecordEnd = {
            Toast.makeText(this,"拍照成功",Toast.LENGTH_SHORT).show()
        }
        val file = File(Environment.getExternalStorageDirectory().absolutePath+"/Download/qqq")
        if (!file.exists()){
            file.mkdirs()
        }
        viewbinding.cameraView.postDelayed({
            viewbinding.cameraView.startRecording(file)
        } ,1000)
        viewbinding.cameraView.postDelayed({
           viewbinding.cameraView.stopRecording()
        },10000)

    }
}