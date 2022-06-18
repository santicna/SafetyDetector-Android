package ar.edu.ort.camerax

//import kotlinx.android.synthetic.main.activity_main.*

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var camera_capture_button: Button
    private lateinit var viewFinder: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CAMERA_PERMISSION)
        }

        camera_capture_button = findViewById<android.widget.Button>(R.id.camera_capture_button)
        viewFinder = findViewById(R.id.viewFinder)
        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun convertImageToBase64String(@NonNull path : String): String {
        val bitmap = BitmapFactory.decodeFile(path)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        return Base64.getEncoder().encodeToString(outputStream.toByteArray())
    }

    private fun getResponseBody(@NonNull connection : HttpURLConnection) : String {
        try {
            val responseReader =
                if (connection.responseCode in 100..399) {
                    BufferedReader(InputStreamReader(connection.inputStream));
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream));
                }
            val stringBuilder = StringBuilder()
            var output: String?
            while (responseReader.readLine().also { output = it } != null) {
                stringBuilder.append(output)
            }
            return stringBuilder.toString()
        } catch (exception : Exception) {
            Log.e(TAG, "No se pudo leer la respuesta del servidor", exception)
            return ""
         }
    }

    private fun sendImage(@NonNull imageBase64: String, @NonNull imageName: String) {
        Thread {
            // Corre en otro thread porque Android no permite el uso de internet en el thread
            // principal para no bloquear la UI

            val msg = "Enviando foto para analizar"
            runOnUiThread {
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, msg)

            val url = URL("http://localhost:5255/api/Images")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json;charset=utf-8")
            connection.setRequestProperty("Accept", "application/json;charset=utf-8")
            connection.doOutput = true

            val jsonRequest = """
                    {
                        "base64ImageString": "$imageBase64",
                        "imageName": "$imageName"
                    }
            """

            connection.outputStream.use { outputStream ->
                val input: ByteArray = jsonRequest.toByteArray(Charsets.UTF_8)
                outputStream.write(input, 0, input.size)
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val msg = "Se envió la foto con éxito"
                runOnUiThread {
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, msg)
            } else {
                val msg = "Ocurrió un error enviando la foto"
                Log.d(TAG, msg)
                runOnUiThread {
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                }
            }
            Log.d(TAG, "El servidor respondió con statusCode=${connection.responseCode} y body=${getResponseBody(connection)}")
        }.start()
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT, Locale.US
                ).format(System.currentTimeMillis()) + ".jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        val callback = object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                val msg = "No se pudo tomar la foto: ${exception.message}"
                Log.e(TAG, msg, exception)
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val encodedImage = convertImageToBase64String(photoFile.absolutePath)
                    sendImage(encodedImage, photoFile.name)
                } catch (exception : Exception) {
                    Toast.makeText(baseContext, "Hubo un error desconocido", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, exception.message, exception)
                }

            }
        }
        val executor = ContextCompat.getMainExecutor(this)
        imageCapture.takePicture(outputOptions, executor, callback)
        
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }

            imageCapture = ImageCapture.Builder()
                    .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CAMERA_PERMISSION = 10
    }
}
