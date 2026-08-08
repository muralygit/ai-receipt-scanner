package com.muraly.receiptscanner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.muraly.receiptscanner.ReceiptScannerApplication
import com.muraly.receiptscanner.databinding.ActivityScanBinding
import com.muraly.receiptscanner.ui.viewmodel.ScanUiState
import com.muraly.receiptscanner.ui.viewmodel.ScanViewModel
import com.muraly.receiptscanner.ui.viewmodel.ViewModelFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private val app by lazy { application as ReceiptScannerApplication }
    private val viewModel: ScanViewModel by viewModels {
        ViewModelFactory(app.repository, app.securePrefs)
    }

    private var imageCapture: ImageCapture? = null
    private var capturedImageFile: File? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, "Camera permission is required to scan receipts", Toast.LENGTH_LONG).show()
        }
    }

    private val pickImageFromGallery = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processGalleryImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCapture.setOnClickListener { capturePhoto() }
        binding.btnGallery.setOnClickListener { pickImageFromGallery.launch("image/*") }
        binding.btnRetry.setOnClickListener { resetToCameraView() }

        viewModel.uiState.observe(this) { state -> renderState(state) }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Could not start camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val photoFile = File(cacheDir, "RECEIPT_$fileName.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedImageFile = photoFile
                    processCapturedFile(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@ScanActivity, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun processCapturedFile(file: File) {
        val bitmap = decodeSampledBitmap(file.absolutePath)
        if (bitmap == null) {
            Toast.makeText(this, "Could not read captured image", Toast.LENGTH_SHORT).show()
            return
        }
        binding.previewView.visibility = android.view.View.GONE
        binding.captureControls.visibility = android.view.View.GONE
        viewModel.processReceiptImage(bitmap, file.absolutePath)
    }

    private fun processGalleryImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Copy into our own cache file so we have a stable path to store/share later.
            val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
            val destFile = File(cacheDir, "RECEIPT_GALLERY_$fileName.jpg")
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            capturedImageFile = destFile

            binding.previewView.visibility = android.view.View.GONE
            binding.captureControls.visibility = android.view.View.GONE
            viewModel.processReceiptImage(bitmap, destFile.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load image from gallery: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Downsamples large photos so OCR + memory stay well-behaved on lower-end devices. */
    private fun decodeSampledBitmap(path: String, maxDimension: Int = 2000): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)

        var sampleSize = 1
        while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val finalOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, finalOptions)
    }

    private fun renderState(state: ScanUiState) {
        when (state) {
            is ScanUiState.Idle -> {
                binding.loadingOverlay.visibility = android.view.View.GONE
            }
            is ScanUiState.OcrProcessing -> {
                binding.loadingOverlay.visibility = android.view.View.VISIBLE
                binding.tvLoadingLabel.text = getString(com.muraly.receiptscanner.R.string.status_reading_text)
                binding.btnRetry.visibility = android.view.View.GONE
            }
            is ScanUiState.AiProcessing -> {
                binding.loadingOverlay.visibility = android.view.View.VISIBLE
                binding.tvLoadingLabel.text = getString(com.muraly.receiptscanner.R.string.status_ai_extracting)
                binding.btnRetry.visibility = android.view.View.GONE
            }
            is ScanUiState.Success -> {
                binding.loadingOverlay.visibility = android.view.View.GONE
                val resultJson = Gson().toJson(state.parsedResult)
                val intent = android.content.Intent(this, ReviewActivity::class.java).apply {
                    putExtra(ReviewActivity.EXTRA_PARSED_RESULT_JSON, resultJson)
                    putExtra(ReviewActivity.EXTRA_IMAGE_URI, state.imageUri)
                    putExtra(ReviewActivity.EXTRA_RAW_OCR_TEXT, state.rawOcrText)
                }
                startActivity(intent)
                finish()
            }
            is ScanUiState.Error -> {
                binding.loadingOverlay.visibility = android.view.View.VISIBLE
                binding.tvLoadingLabel.text = state.message
                binding.btnRetry.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun resetToCameraView() {
        binding.loadingOverlay.visibility = android.view.View.GONE
        binding.previewView.visibility = android.view.View.VISIBLE
        binding.captureControls.visibility = android.view.View.VISIBLE
    }
}
