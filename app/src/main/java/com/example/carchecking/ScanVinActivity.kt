package com.example.carchecking

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class ScanVinActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvHint: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var btnTorch: ImageButton

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var torchOn = false
    private var analyzing = false
    private var lastVin: String? = null

    private val exec = Executors.newSingleThreadExecutor()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // ROI(세로 띠): 라이브 분석은 ROI 제한, 폴백 캡처는 전체 프레임
    private val roiTopRatio = 0.40f
    private val roiBottomRatio = 0.60f

    // 폴백 타이머
    private val main = Handler(Looper.getMainLooper())
    private var lastSeenTs = System.currentTimeMillis()
    private val fallbackMs = 2500L
    private val ticker = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                if (System.currentTimeMillis() - lastSeenTs > fallbackMs) {
                    // 자동 폴백: 고해상도 1장 캡처 후 정밀 인식
                    captureAndRecognize()
                    lastSeenTs = System.currentTimeMillis() // 재시도 간격 조절
                }
                main.postDelayed(this, 1200L)
            }
        }
    }

    private val reqPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> if (ok) startCamera() else finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_vin)

        previewView = findViewById(R.id.preview)
        tvHint = findViewById(R.id.tvHint)
        btnClose = findViewById(R.id.btnClose)
        btnTorch = findViewById(R.id.btnTorch)

        btnClose.setOnClickListener { finish() }
        btnTorch.setOnClickListener {
            torchOn = !torchOn
            camera?.cameraControl?.enableTorch(torchOn)
            btnTorch.alpha = if (torchOn) 1f else 0.5f
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            reqPerm.launch(Manifest.permission.CAMERA)
        } else startCamera()
    }

    override fun onResume() {
        super.onResume()
        main.postDelayed(ticker, 1200L)
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(ticker)
    }

    private fun startCamera() {
        val providerFut = ProcessCameraProvider.getInstance(this)
        providerFut.addListener({
            val provider = providerFut.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
                    setAnalyzer(exec) { imgProxy -> analyzeLive(imgProxy) }
                }

            camera = provider.bindToLifecycle(this, selector, preview, analysis, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun isInRoi(box: android.graphics.Rect, rotationDeg: Int, mediaWidth: Int, mediaHeight: Int): Boolean {
        val rotated = (rotationDeg % 180 != 0)
        val imgH = if (rotated) mediaWidth else mediaHeight
        val centerY = if (rotated) box.centerX() else box.centerY()
        val yNorm = centerY.toFloat() / imgH
        return yNorm in roiTopRatio..roiBottomRatio
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeLive(img: ImageProxy) {
        if (analyzing) { img.close(); return }
        val media = img.image ?: run { img.close(); return }
        analyzing = true

        val rotation = img.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(media, rotation)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                var found: String? = null
                var showed = false

                for (block in result.textBlocks) {
                    val box = block.boundingBox ?: continue
                    if (!isInRoi(box, rotation, media.width, media.height)) continue

                    val candidates = VinUtils.extractCandidates(block.text)
                    if (!showed && candidates.isNotEmpty()) {
                        showed = true
                        lastSeenTs = System.currentTimeMillis()
                        tvHint.post {
                            tvHint.text = "후보(ROI): ${candidates.take(2).joinToString(", ")}"
                            tvHint.visibility = View.VISIBLE
                        }
                    }
                    val valid = candidates.firstOrNull { VinUtils.isValidVin(it) }
                    if (valid != null) { found = valid; break }
                }

                if (found != null && found != lastVin) {
                    lastVin = found
                    runCatching { LogBus.vinScan(found!!) }
                    showConfirmDialog(found!!)
                }
            }
            .addOnCompleteListener {
                analyzing = false
                img.close()
            }
    }

    /** 폴백: 고해상도 1장 캡처 → 프레임 전체에서 정밀 인식(ROI 미제한) */
    private fun captureAndRecognize() {
        val cap = imageCapture ?: return
        if (analyzing) return
        analyzing = true
        runCatching { LogBus.logRaw("VIN 캡처 폴백 시도") }

        cap.takePicture(exec, object : ImageCapture.OnImageCapturedCallback() {
            @OptIn(ExperimentalGetImage::class)
            override fun onCaptureSuccess(image: ImageProxy) {
                val media = image.image
                if (media == null) { image.close(); analyzing = false; return }
                val rotation = image.imageInfo.rotationDegrees
                val input = InputImage.fromMediaImage(media, rotation)

                recognizer.process(input)
                    .addOnSuccessListener { result ->
                        var found: String? = null
                        // 전체 텍스트에서 라벨 우선 → 일반 패턴으로 스캔
                        val allText = result.text
                        val pref = VinUtils.extractPreferLabeledArea(allText)
                        found = pref.firstOrNull() ?: VinUtils.extractAll(allText).firstOrNull()

                        if (found != null && found != lastVin) {
                            lastVin = found
                            runCatching { LogBus.vinScan(found!!) }
                            showConfirmDialog(found!!)
                        } else {
                            tvHint.post {
                                tvHint.text = "캡처 분석: 후보 없음"
                                tvHint.visibility = View.VISIBLE
                            }
                        }
                    }
                    .addOnCompleteListener {
                        image.close()
                        analyzing = false
                    }
            }
            override fun onError(exc: ImageCaptureException) {
                runCatching { LogBus.logRaw("VIN 캡처 폴백 실패: ${exc.message ?: exc.toString()}") }
                analyzing = false
            }
        })
    }

    private fun showConfirmDialog(vin: String) {
        // 상단 라벨
        tvHint.post {
            tvHint.text = "인식됨: $vin"
            tvHint.visibility = View.VISIBLE
        }
        // 진동
        runCatching {
            vibrator()?.let {
                if (Build.VERSION.SDK_INT >= 26)
                    it.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") it.vibrate(40)
            }
        }
        // 확인창
        AlertDialog.Builder(this)
            .setTitle("차대번호 확인")
            .setMessage(vin)
            .setNegativeButton("취소", null)
            .setNeutralButton("복사") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("VIN", vin))
                tvHint.text = "복사됨: $vin"; tvHint.visibility = View.VISIBLE
            }
            .setPositiveButton("확인") { _, _ ->
                setResult(RESULT_OK, Intent().putExtra("vin", vin))
                finish()
            }
            .show()
    }

    private fun vibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (_: Exception) { null }
}
