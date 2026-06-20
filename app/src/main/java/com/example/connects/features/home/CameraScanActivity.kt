package com.example.connects.features.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlKitText
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

private const val CAMERA_TAG = "CameraScanActivity"
private const val SCAN_FRAME_SIZE_DP = 280f
private const val GRID_CLUSTER_MIN_DISTANCE_PX = 12f
private const val AUTO_OCR_MIN_SCORE = 0.0f
private const val OCR_LOG_TEXT_LIMIT = 320
private const val AUTO_OCR_MIN_TEXT_SCORE = 45
private const val AUTO_OCR_MIN_TEXT_LENGTH = 8

private fun cameraLogSnippet(text: String, maxLen: Int = OCR_LOG_TEXT_LIMIT): String {
    val compact = text
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    if (compact.length <= maxLen) return compact
    return compact.take(maxLen) + "..."
}

private fun RectF.toLogRect(): String {
    return String.format(
        Locale.US,
        "[l=%.3f,t=%.3f,r=%.3f,b=%.3f,w=%.3f,h=%.3f]",
        left,
        top,
        right,
        bottom,
        width(),
        height()
    )
}

private fun tableStructureSummary(structure: TableStructure?): String {
    return if (structure == null) "none" else "${structure.rowCount}x${structure.colCount}"
}

private data class OcrToken(
    val text: String,
    val rect: RectF
)

private data class FrameCandidate(
    val sharpness: Float,
    val bitmap: Bitmap
)

private data class OcrEngineResult(
    val source: String,
    val vision: MlKitText,
    val rawText: String,
    val normalizedText: String,
    val score: Int
)

data class TableStructure(
    val rowCount: Int,
    val colCount: Int,
    val rowBoundariesNorm: List<Float>,
    val colBoundariesNorm: List<Float>,
    val cells: List<List<String>>
)

private data class ViewRectTransform(
    val imageLeftInViewPx: Float,
    val imageTopInViewPx: Float,
    val imageDrawWidthPx: Float,
    val imageDrawHeightPx: Float
)

class CameraScanActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        setContent {
            MaterialTheme {
                RealtimeScannerScreen(
                    cameraExecutor = cameraExecutor,
                    onTextDetected = { detectedText, tableStructure ->
                        setResult(RESULT_OK, Intent().apply {
                            putExtra("scanned_text", detectedText)
                            if (tableStructure != null) {
                                putExtra("scanned_rows", tableStructure.rowCount)
                                putExtra("scanned_cols", tableStructure.colCount)
                                putExtra("scanned_table_json", toTableJson(tableStructure))
                            }
                        })
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

private fun isNutritionLabel(text: String): Boolean {
    if (text.length < 10) return false

    val lowerText = text.lowercase()
    val nutritionKeywords = listOf(
        "蛋白", "protein",
        "脂肪", "fat",
        "碳水", "carbohydrate", "carbs",
        "纤维", "fiber",
        "钠", "sodium",
        "能量", "热量", "energy", "calorie", "calories",
        "营养", "每100", "每份", "nutrition"
    )

    val hasKeywords = nutritionKeywords.count { lowerText.contains(it) } >= 2
    val hasDigits = Regex("\\d").containsMatchIn(lowerText)
    return hasKeywords && hasDigits
}
private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun normalizeRect(rect: RectF, width: Int, height: Int): RectF {
    val w = width.toFloat().coerceAtLeast(1f)
    val h = height.toFloat().coerceAtLeast(1f)
    return RectF(rect.left / w, rect.top / h, rect.right / w, rect.bottom / h)
}

private fun denormalizeRect(rect: RectF, width: Int, height: Int): RectF {
    val w = width.toFloat().coerceAtLeast(1f)
    val h = height.toFloat().coerceAtLeast(1f)
    return RectF(
        (rect.left * w).coerceIn(0f, w - 1f),
        (rect.top * h).coerceIn(0f, h - 1f),
        (rect.right * w).coerceIn(1f, w),
        (rect.bottom * h).coerceIn(1f, h)
    )
}

private fun buildCenterCropTransform(
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Float,
    viewHeight: Float
): ViewRectTransform {
    val iw = imageWidth.toFloat().coerceAtLeast(1f)
    val ih = imageHeight.toFloat().coerceAtLeast(1f)
    val vw = viewWidth.coerceAtLeast(1f)
    val vh = viewHeight.coerceAtLeast(1f)

    val scale = max(vw / iw, vh / ih)
    val drawW = iw * scale
    val drawH = ih * scale
    val left = (vw - drawW) / 2f
    val top = (vh - drawH) / 2f
    return ViewRectTransform(
        imageLeftInViewPx = left,
        imageTopInViewPx = top,
        imageDrawWidthPx = drawW,
        imageDrawHeightPx = drawH
    )
}

private fun imageNormRectToViewRect(
    rectNorm: RectF,
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Float,
    viewHeight: Float
): RectF {
    val t = buildCenterCropTransform(imageWidth, imageHeight, viewWidth, viewHeight)
    val l = t.imageLeftInViewPx + rectNorm.left.coerceIn(0f, 1f) * t.imageDrawWidthPx
    val tp = t.imageTopInViewPx + rectNorm.top.coerceIn(0f, 1f) * t.imageDrawHeightPx
    val r = t.imageLeftInViewPx + rectNorm.right.coerceIn(0f, 1f) * t.imageDrawWidthPx
    val b = t.imageTopInViewPx + rectNorm.bottom.coerceIn(0f, 1f) * t.imageDrawHeightPx
    return RectF(l, tp, r, b)
}

private fun expandAndClampRect(rect: RectF, width: Int, height: Int, paddingRatio: Float = 0.06f): RectF {
    val dw = rect.width() * paddingRatio
    val dh = rect.height() * paddingRatio

    return RectF(
        (rect.left - dw).coerceIn(0f, width.toFloat() - 1f),
        (rect.top - dh).coerceIn(0f, height.toFloat() - 1f),
        (rect.right + dw).coerceIn(1f, width.toFloat()),
        (rect.bottom + dh).coerceIn(1f, height.toFloat())
    )
}

private fun cropBitmap(bitmap: Bitmap, rect: RectF): Bitmap {
    val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
    val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
    val right = rect.right.toInt().coerceIn(left + 1, bitmap.width)
    val bottom = rect.bottom.toInt().coerceIn(top + 1, bitmap.height)

    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

private fun computeSharpness(bitmap: Bitmap): Float {
    if (bitmap.width < 3 || bitmap.height < 3) return 0f
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    fun grayAt(x: Int, y: Int): Float {
        val c = pixels[y * w + x]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b)
    }

    var sum = 0f
    var sumSq = 0f
    var count = 0
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val center = grayAt(x, y) * -4f
            val lap = center + grayAt(x - 1, y) + grayAt(x + 1, y) + grayAt(x, y - 1) + grayAt(x, y + 1)
            sum += lap
            sumSq += lap * lap
            count++
        }
    }
    if (count <= 1) return 0f
    val mean = sum / count.toFloat()
    return (sumSq / count.toFloat()) - mean * mean
}

private fun otsuThreshold(gray: IntArray): Int {
    if (gray.isEmpty()) return 127
    val hist = IntArray(256)
    for (v in gray) {
        hist[v.coerceIn(0, 255)]++
    }

    val total = gray.size
    var sumAll = 0.0
    for (i in 0..255) {
        sumAll += i.toDouble() * hist[i].toDouble()
    }

    var sumB = 0.0
    var wB = 0
    var maxVar = -1.0
    var threshold = 127

    for (t in 0..255) {
        wB += hist[t]
        if (wB == 0) continue
        val wF = total - wB
        if (wF == 0) break

        sumB += t.toDouble() * hist[t].toDouble()
        val mB = sumB / wB.toDouble()
        val mF = (sumAll - sumB) / wF.toDouble()
        val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
        if (between > maxVar) {
            maxVar = between
            threshold = t
        }
    }
    return threshold
}

private fun enhanceBitmapForOcr(src: Bitmap): Bitmap {
    val w = src.width.coerceAtLeast(1)
    val h = src.height.coerceAtLeast(1)
    val size = w * h
    val pixels = IntArray(size)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    val gray = IntArray(size)
    val hist = IntArray(256)
    for (i in 0 until size) {
        val c = pixels[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val y = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
        gray[i] = y
        hist[y]++
    }

    fun percentile(p: Float): Int {
        val target = (size * p).toInt().coerceIn(0, size - 1)
        var running = 0
        for (i in 0..255) {
            running += hist[i]
            if (running > target) return i
        }
        return 255
    }

    val low = percentile(0.03f)
    val high = percentile(0.97f).coerceAtLeast(low + 1)

    val stretched = IntArray(size)
    for (i in 0 until size) {
        val v = ((gray[i] - low) * 255) / (high - low)
        stretched[i] = v.coerceIn(0, 255)
    }

    val sharp = stretched.clone()
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val idx = y * w + x
            val v = stretched[idx] * 5 -
                stretched[idx - 1] -
                stretched[idx + 1] -
                stretched[idx - w] -
                stretched[idx + w]
            sharp[idx] = v.coerceIn(0, 255)
        }
    }

    val th = otsuThreshold(sharp)
    val outPixels = IntArray(size)
    var darkCount = 0
    for (i in 0 until size) {
        val bw = if (sharp[i] <= th) 0 else 255
        if (bw == 0) darkCount++
        outPixels[i] = (0xFF shl 24) or (bw shl 16) or (bw shl 8) or bw
    }

    if (darkCount > (size * 0.55f).toInt()) {
        for (i in outPixels.indices) {
            val v = outPixels[i] and 0xFF
            val inv = 255 - v
            outPixels[i] = (0xFF shl 24) or (inv shl 16) or (inv shl 8) or inv
        }
    }

    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(outPixels, 0, w, 0, 0, w, h)
    }
}

private fun ocrTextScore(text: String): Int {
    val t = text.trim().lowercase()
    if (t.isEmpty()) return 0
    val keywords = listOf(
        "蛋白", "protein",
        "脂肪", "fat",
        "碳水", "carbohydrate", "carbs",
        "纤维", "fiber",
        "钠", "sodium",
        "能量", "热量", "energy", "calorie", "calories",
        "每100", "每份"
    )
    val hit = keywords.count { t.contains(it) }
    val digits = t.count { it.isDigit() }
    return t.length + hit * 25 + digits * 2
}

private fun normalizeOcrNumericToken(token: String): String {
    val mapped = StringBuilder(token.length)
    for (ch in token) {
        val fixed = when (ch) {
            'I', 'l', '|' -> '1'
            'O', 'o', 'D' -> '0'
            'S', 's' -> '5'
            'B' -> '8'
            'Z', 'z' -> '2'
            'G' -> '6'
            'T' -> '7'
            else -> ch
        }
        mapped.append(fixed)
    }
    return mapped.toString()
}

private fun normalizeOcrText(rawText: String): String {
    var text = rawText
        .replace('：', ':')
        .replace('，', ',')
        .replace('。', '.')
        .replace('｜', '|')
        .replace('（', '(')
        .replace('）', ')')
        .replace('|', '\n')
        .replace(Regex("[ ]+"), " ")
        .replace(Regex("[\r\t]+"), " ")
        .trim()

    text = text.replace(Regex("(?i)k[h]"), "kJ")
    text = text.replace(Regex("(?i)kca[1il]"), "kcal")
    text = text.replace(Regex("(?i)营?养声"), "营养")
    text = text.replace(Regex("(?i)营养或分表"), "营养成分表")
    text = text.replace(Regex("(?i)营养称以"), "营养成分")
    text = text.replace(Regex("(?i)营养戊分"), "营养成分")
    text = text.replace(Regex("(?i)蛋[日白目]质"), "蛋白质")
    text = text.replace(Regex("(?i)脂[防肪肪]"), "脂肪")
    text = text.replace(Regex("(?i)碳水化[台合]物"), "碳水化合物")
    text = text.replace(Regex("(?i)碳水化台"), "碳水化")
    text = text.replace(Regex("(?i)钠含[童量]"), "钠含量")
    text = text.replace(Regex("(?i)蛋日"), "蛋白")
    text = text.replace(Regex("(?i)热童"), "热量")
    text = text.replace(Regex("(?i)能童"), "能量")

    val unitPattern = Regex("([0-9A-Za-z|]{1,10})\\s*(mg|g|kJ|kj|kcal)", RegexOption.IGNORE_CASE)
    text = unitPattern.replace(text) { mr ->
        val value = normalizeOcrNumericToken(mr.groupValues[1])
        val unitRaw = mr.groupValues[2].lowercase()
        val unit = when (unitRaw) {
            "kj" -> "kJ"
            else -> unitRaw
        }
        "$value$unit"
    }

    return text
}

private fun macroKeywordHitCount(text: String): Int {
    val t = text.lowercase()
    val keywords = listOf(
        "蛋白", "protein",
        "脂肪", "fat",
        "碳水", "carbohydrate", "carbs"
    )
    return keywords.count { t.contains(it) }
}

private fun isOcrTextUsable(text: String, score: Int): Boolean {
    val normalized = text.lowercase()
    val keywordHit = listOf(
        "营养", "蛋白", "脂肪", "碳水", "钠", "能量", "热量",
        "protein", "fat", "carbohydrate", "sodium", "energy"
    ).count { normalized.contains(it) }
    val macroHit = macroKeywordHitCount(normalized)
    val digitCount = normalized.count { it.isDigit() }
    val numberTokenCount = Regex("\\d+(?:\\.\\d+)?").findAll(normalized).count()
    val hasUnit = normalized.contains("g") || normalized.contains("mg") ||
        normalized.contains("kj") || normalized.contains("kcal")
    val longEnough = normalized.length >= AUTO_OCR_MIN_TEXT_LENGTH
    val strongNutritionHit = macroHit >= 1 && (keywordHit >= 2 || (keywordHit >= 1 && digitCount >= 3))
    val numericFallback = keywordHit >= 1 && hasUnit && numberTokenCount >= 2
    return longEnough && score >= AUTO_OCR_MIN_TEXT_SCORE &&
        (strongNutritionHit || numericFallback)
}

private fun runRecognizerPair(
    input: InputImage,
    sourceLabel: String,
    chineseRecognizer: TextRecognizer,
    latinRecognizer: TextRecognizer,
    onSuccess: (OcrEngineResult) -> Unit,
    onFailure: (Exception) -> Unit
) {
    chineseRecognizer.process(input)
        .addOnSuccessListener { zhVision ->
            val zhRaw = zhVision.text
            val zhNormalized = normalizeOcrText(zhRaw)
            val zhScore = ocrTextScore(zhNormalized)
            val zhResult = OcrEngineResult(
                source = "${sourceLabel}_zh",
                vision = zhVision,
                rawText = zhRaw,
                normalizedText = zhNormalized,
                score = zhScore
            )
            Log.i(
                CAMERA_TAG,
                "OCR $sourceLabel zh score=$zhScore rawLen=${zhRaw.length} normalizedLen=${zhNormalized.length} snippet=\"${cameraLogSnippet(zhNormalized)}\""
            )

            latinRecognizer.process(input)
                .addOnSuccessListener { latinVision ->
                    val latinRaw = latinVision.text
                    val latinNormalized = normalizeOcrText(latinRaw)
                    val latinScore = ocrTextScore(latinNormalized)
                    val latinResult = OcrEngineResult(
                        source = "${sourceLabel}_latin",
                        vision = latinVision,
                        rawText = latinRaw,
                        normalizedText = latinNormalized,
                        score = latinScore
                    )
                    Log.i(
                        CAMERA_TAG,
                        "OCR $sourceLabel latin score=$latinScore rawLen=${latinRaw.length} normalizedLen=${latinNormalized.length} snippet=\"${cameraLogSnippet(latinNormalized)}\""
                    )
                    val best = if (latinResult.score > zhResult.score) latinResult else zhResult
                    Log.i(
                        CAMERA_TAG,
                        "OCR $sourceLabel choose=${best.source} score=${best.score}"
                    )
                    onSuccess(best)
                }
                .addOnFailureListener { latinError ->
                    Log.w(CAMERA_TAG, "OCR $sourceLabel latin failed; fallback zh", latinError)
                    onSuccess(zhResult)
                }
        }
        .addOnFailureListener { zhError ->
            Log.w(CAMERA_TAG, "OCR $sourceLabel zh failed; fallback latin", zhError)
            latinRecognizer.process(input)
                .addOnSuccessListener { latinVision ->
                    val latinRaw = latinVision.text
                    val latinNormalized = normalizeOcrText(latinRaw)
                    val latinScore = ocrTextScore(latinNormalized)
                    val latinResult = OcrEngineResult(
                        source = "${sourceLabel}_latin",
                        vision = latinVision,
                        rawText = latinRaw,
                        normalizedText = latinNormalized,
                        score = latinScore
                    )
                    Log.i(
                        CAMERA_TAG,
                        "OCR $sourceLabel latin-only score=$latinScore rawLen=${latinRaw.length} normalizedLen=${latinNormalized.length} snippet=\"${cameraLogSnippet(latinNormalized)}\""
                    )
                    onSuccess(latinResult)
                }
                .addOnFailureListener { latinError ->
                    onFailure(RuntimeException("OCR $sourceLabel failed on both zh/latin", latinError))
                }
        }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val nv21 = yuv420888ToNv21(imageProxy)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        val ok = yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            95,
            out
        )
        if (!ok) return null
        val bytes = out.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e(CAMERA_TAG, "Convert ImageProxy to Bitmap failed", e)
        null
    }
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 4
    val nv21 = ByteArray(ySize + uvSize * 2)

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    copyPlane(
        srcBuffer = yPlane.buffer,
        srcRowStride = yPlane.rowStride,
        srcPixelStride = yPlane.pixelStride,
        width = width,
        height = height,
        out = nv21,
        outOffset = 0,
        outPixelStride = 1
    )

    // NV21 uses VU order.
    copyPlane(
        srcBuffer = vPlane.buffer,
        srcRowStride = vPlane.rowStride,
        srcPixelStride = vPlane.pixelStride,
        width = width / 2,
        height = height / 2,
        out = nv21,
        outOffset = ySize,
        outPixelStride = 2
    )
    copyPlane(
        srcBuffer = uPlane.buffer,
        srcRowStride = uPlane.rowStride,
        srcPixelStride = uPlane.pixelStride,
        width = width / 2,
        height = height / 2,
        out = nv21,
        outOffset = ySize + 1,
        outPixelStride = 2
    )

    return nv21
}

private fun copyPlane(
    srcBuffer: ByteBuffer,
    srcRowStride: Int,
    srcPixelStride: Int,
    width: Int,
    height: Int,
    out: ByteArray,
    outOffset: Int,
    outPixelStride: Int
) {
    val buffer = srcBuffer.duplicate()
    for (row in 0 until height) {
        val rowStart = row * srcRowStride
        for (col in 0 until width) {
            val srcIndex = rowStart + col * srcPixelStride
            val outIndex = outOffset + (row * width + col) * outPixelStride
            out[outIndex] = buffer.get(srcIndex)
        }
    }
}

private fun extractOcrTokens(visionText: MlKitText): List<OcrToken> {
    val tokens = mutableListOf<OcrToken>()
    for (block in visionText.textBlocks) {
        for (line in block.lines) {
            if (line.elements.isNotEmpty()) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    val value = element.text.trim()
                    if (value.isNotEmpty()) {
                        tokens += OcrToken(value, RectF(box))
                    }
                }
            } else {
                val box = line.boundingBox ?: continue
                val value = line.text.trim()
                if (value.isNotEmpty()) {
                    tokens += OcrToken(value, RectF(box))
                }
            }
        }
    }
    return tokens
}

private fun clusterCenters(values: List<Float>, minDistance: Float): List<Float> {
    if (values.isEmpty()) return emptyList()
    val sorted = values.sorted()
    val groups = mutableListOf<MutableList<Float>>()
    var current = mutableListOf(sorted.first())

    for (i in 1 until sorted.size) {
        val v = sorted[i]
        if (abs(v - current.last()) <= minDistance) {
            current += v
        } else {
            groups += current
            current = mutableListOf(v)
        }
    }
    groups += current

    return groups.map { group ->
        (group.sum() / group.size.toFloat())
    }
}

private fun buildBoundariesPx(centers: List<Float>, totalSizePx: Float): List<Float> {
    if (centers.isEmpty() || totalSizePx <= 0f) {
        return listOf(0f, totalSizePx.coerceAtLeast(1f))
    }

    val sorted = centers.sorted()
    val boundaries = mutableListOf<Float>()
    boundaries += 0f
    for (i in 0 until sorted.lastIndex) {
        boundaries += ((sorted[i] + sorted[i + 1]) / 2f).coerceIn(0f, totalSizePx)
    }
    boundaries += totalSizePx

    val fixed = mutableListOf<Float>()
    for ((index, b) in boundaries.withIndex()) {
        if (index == 0) {
            fixed += b.coerceIn(0f, totalSizePx)
            continue
        }
        val prev = fixed.last()
        val next = max(prev + 1f, b).coerceIn(0f, totalSizePx)
        fixed += next
    }
    if (fixed.size < 2) {
        return listOf(0f, totalSizePx.coerceAtLeast(1f))
    }
    return fixed
}

private fun indexForCoordinate(value: Float, boundaries: List<Float>): Int {
    if (boundaries.size < 2) return -1
    for (i in 0 until boundaries.lastIndex) {
        val start = boundaries[i]
        val end = boundaries[i + 1]
        if (value >= start && value < end) return i
    }
    return boundaries.lastIndex - 1
}

private fun buildTableStructure(
    visionText: MlKitText,
    roiWidth: Int,
    roiHeight: Int
): TableStructure? {
    if (roiWidth <= 0 || roiHeight <= 0) return null

    val tokens = extractOcrTokens(visionText)
    if (tokens.isEmpty()) return null

    val rowCenters = tokens.map { (it.rect.top + it.rect.bottom) / 2f }
    val colCenters = tokens.map { (it.rect.left + it.rect.right) / 2f }

    val rowClusterDistance = max(GRID_CLUSTER_MIN_DISTANCE_PX, roiHeight * 0.035f)
    val colClusterDistance = max(GRID_CLUSTER_MIN_DISTANCE_PX, roiWidth * 0.05f)

    val rowAnchors = clusterCenters(rowCenters, rowClusterDistance)
    val colAnchors = clusterCenters(colCenters, colClusterDistance)
    if (rowAnchors.isEmpty() || colAnchors.isEmpty()) return null

    val rowBoundariesPx = buildBoundariesPx(rowAnchors, roiHeight.toFloat())
    val colBoundariesPx = buildBoundariesPx(colAnchors, roiWidth.toFloat())
    val rowCount = (rowBoundariesPx.size - 1).coerceAtLeast(1)
    val colCount = (colBoundariesPx.size - 1).coerceAtLeast(1)

    val matrix = MutableList(rowCount) { MutableList(colCount) { "" } }

    val sortedTokens = tokens.sortedWith(compareBy<OcrToken> { it.rect.top }.thenBy { it.rect.left })
    for (token in sortedTokens) {
        val midX = (token.rect.left + token.rect.right) / 2f
        val midY = (token.rect.top + token.rect.bottom) / 2f
        val row = indexForCoordinate(midY, rowBoundariesPx)
        val col = indexForCoordinate(midX, colBoundariesPx)
        if (row !in 0 until rowCount || col !in 0 until colCount) continue

        matrix[row][col] = if (matrix[row][col].isBlank()) {
            token.text
        } else {
            "${matrix[row][col]} ${token.text}"
        }
    }

    val rowNorm = rowBoundariesPx.map { (it / roiHeight.toFloat()).coerceIn(0f, 1f) }
    val colNorm = colBoundariesPx.map { (it / roiWidth.toFloat()).coerceIn(0f, 1f) }

    return TableStructure(
        rowCount = rowCount,
        colCount = colCount,
        rowBoundariesNorm = rowNorm,
        colBoundariesNorm = colNorm,
        cells = matrix.map { it.toList() }
    )
}

private fun toTableJson(structure: TableStructure): String {
    val root = JSONObject()
    root.put("rows", structure.rowCount)
    root.put("cols", structure.colCount)

    val rowBoundaries = JSONArray()
    structure.rowBoundariesNorm.forEach { rowBoundaries.put(it.toDouble()) }
    val colBoundaries = JSONArray()
    structure.colBoundariesNorm.forEach { colBoundaries.put(it.toDouble()) }
    root.put("rowBoundaries", rowBoundaries)
    root.put("colBoundaries", colBoundaries)

    val cells = JSONArray()
    structure.cells.forEach { row ->
        val rowArray = JSONArray()
        row.forEach { rowArray.put(it) }
        cells.put(rowArray)
    }
    root.put("cells", cells)
    return root.toString()
}

private fun processImage(
    imageProxy: ImageProxy,
    detector: YoloTableDetector,
    chineseRecognizer: TextRecognizer,
    latinRecognizer: TextRecognizer,
    scanGate: AtomicBoolean,
    selectedBoxRef: AtomicReference<RectF?>,
    ocrRunningRef: AtomicBoolean,
    frameCandidatesRef: AtomicReference<List<FrameCandidate>>,
    onBoxDetected: (RectF?, Int, Int) -> Unit,
    onTableStructure: (TableStructure?) -> Unit = {},
    onResult: (String, TableStructure?) -> Unit
) {
    if (!scanGate.get()) {
        imageProxy.close()
        return
    }

    var rawBitmap: Bitmap? = null
    var uprightBitmap: Bitmap? = null
    var roiBitmap: Bitmap? = null

    try {
        rawBitmap = imageProxyToBitmap(imageProxy)
        if (rawBitmap == null) {
            imageProxy.close()
            return
        }
        uprightBitmap = rotateBitmap(rawBitmap, imageProxy.imageInfo.rotationDegrees)

        val currentSelection = selectedBoxRef.get()

        if (currentSelection == null) {
            // Auto mode: detect table, draw box, then auto-enter OCR when score is stable enough.
            if (detector.isInitialized) {
                val detection = detector.detect(uprightBitmap)
                if (detection != null) {
                    val normalized = normalizeRect(detection.rect, uprightBitmap.width, uprightBitmap.height)
                    Log.d(
                        CAMERA_TAG,
                        String.format(
                            Locale.US,
                            "YOLO detect score=%.3f rectNorm=%s autoThreshold=%.2f",
                            detection.score,
                            normalized.toLogRect(),
                            AUTO_OCR_MIN_SCORE
                        )
                    )
                    onBoxDetected(normalized, uprightBitmap.width, uprightBitmap.height)
                    if (detection.score >= AUTO_OCR_MIN_SCORE) {
                        selectedBoxRef.set(RectF(normalized))
                        Log.i(CAMERA_TAG, "YOLO auto-selected box, entering OCR candidate capture")
                    }
                } else {
                    Log.d(CAMERA_TAG, "YOLO detect: no table found on this frame")
                    onBoxDetected(null, uprightBitmap.width, uprightBitmap.height)
                }
            } else {
                Log.w(CAMERA_TAG, "YOLO detector not initialized yet; skip detection this frame")
                onBoxDetected(null, uprightBitmap.width, uprightBitmap.height)
            }
            imageProxy.close()
            if (uprightBitmap !== rawBitmap && !uprightBitmap.isRecycled) {
                uprightBitmap.recycle()
            }
            if (!rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }
            return
        }

        // Avoid running multiple OCR tasks in parallel.
        if (!ocrRunningRef.compareAndSet(false, true)) {
            Log.d(CAMERA_TAG, "OCR is already running, skip this frame")
            imageProxy.close()
            if (uprightBitmap !== rawBitmap && !uprightBitmap.isRecycled) {
                uprightBitmap.recycle()
            }
            if (!rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }
            return
        }

        val selectedPx = denormalizeRect(currentSelection, uprightBitmap.width, uprightBitmap.height)
        val roi = expandAndClampRect(selectedPx, uprightBitmap.width, uprightBitmap.height)
        roiBitmap = cropBitmap(uprightBitmap, roi)
        Log.d(
            CAMERA_TAG,
            String.format(
                Locale.US,
                "OCR ROI prepared roiPx=[l=%.1f,t=%.1f,r=%.1f,b=%.1f] roiSize=%dx%d",
                roi.left,
                roi.top,
                roi.right,
                roi.bottom,
                roiBitmap.width,
                roiBitmap.height
            )
        )

        val currentCandidates = frameCandidatesRef.get()
        val copied = roiBitmap.copy(Bitmap.Config.ARGB_8888, false)
        val updatedCandidates = (currentCandidates + FrameCandidate(computeSharpness(copied), copied))
            .sortedByDescending { it.sharpness }
            .take(3)
        frameCandidatesRef.set(updatedCandidates)
        Log.d(
            CAMERA_TAG,
            "OCR frame candidates=${updatedCandidates.size} sharpness=${updatedCandidates.joinToString { String.format(Locale.US, "%.2f", it.sharpness) }}"
        )
        if (currentCandidates.size >= 2) {
            val bestFrame = updatedCandidates.first().bitmap
            val imageForOcr = if (bestFrame.width < 1000 || bestFrame.height < 1000) {
                Bitmap.createScaledBitmap(bestFrame, bestFrame.width * 2, bestFrame.height * 2, true)
            } else {
                bestFrame
            }
            Log.i(
                CAMERA_TAG,
                "OCR trigger with ${updatedCandidates.size} frames, bestSharpness=${String.format(Locale.US, "%.2f", updatedCandidates.first().sharpness)}, inputSize=${imageForOcr.width}x${imageForOcr.height}"
            )
            val enhancedForOcr = enhanceBitmapForOcr(imageForOcr)
            frameCandidatesRef.set(emptyList())

            fun cleanupOcr() {
                if (!enhancedForOcr.isRecycled) {
                    enhancedForOcr.recycle()
                }
                if (imageForOcr !== bestFrame && !imageForOcr.isRecycled) {
                    imageForOcr.recycle()
                }
                updatedCandidates.forEach {
                    if (!it.bitmap.isRecycled) {
                        it.bitmap.recycle()
                    }
                }
                ocrRunningRef.set(false)
                imageProxy.close()
                if (roiBitmap !== uprightBitmap && !roiBitmap.isRecycled) {
                    roiBitmap.recycle()
                }
                if (uprightBitmap !== rawBitmap && !uprightBitmap.isRecycled) {
                    uprightBitmap.recycle()
                }
                if (!rawBitmap.isRecycled) {
                    rawBitmap.recycle()
                }
            }

            val rawInput = InputImage.fromBitmap(imageForOcr, 0)
            val enhancedInput = InputImage.fromBitmap(enhancedForOcr, 0)
            var cleaned = false
            fun cleanupOnce() {
                if (cleaned) return
                cleaned = true
                cleanupOcr()
            }

            runRecognizerPair(
                input = rawInput,
                sourceLabel = "raw",
                chineseRecognizer = chineseRecognizer,
                latinRecognizer = latinRecognizer,
                onSuccess = { rawResult ->
                    val rawStructure = buildTableStructure(
                        visionText = rawResult.vision,
                        roiWidth = imageForOcr.width,
                        roiHeight = imageForOcr.height
                    )
                    Log.i(
                        CAMERA_TAG,
                        "OCR raw chosen=${rawResult.source} score=${rawResult.score} table=${tableStructureSummary(rawStructure)} normalized=\"${cameraLogSnippet(rawResult.normalizedText)}\""
                    )

                    runRecognizerPair(
                        input = enhancedInput,
                        sourceLabel = "enhanced",
                        chineseRecognizer = chineseRecognizer,
                        latinRecognizer = latinRecognizer,
                        onSuccess = { enhancedResult ->
                            val bestResult = if (enhancedResult.score > rawResult.score) enhancedResult else rawResult
                            val enhancedStructure = buildTableStructure(
                                visionText = enhancedResult.vision,
                                roiWidth = enhancedForOcr.width,
                                roiHeight = enhancedForOcr.height
                            )
                            val structure = rawStructure ?: enhancedStructure
                            onTableStructure(structure)

                            val bestText = bestResult.normalizedText.trim()
                            val usable = isOcrTextUsable(bestText, bestResult.score)
                            Log.i(
                                CAMERA_TAG,
                                "OCR final choose=${bestResult.source} score=${bestResult.score} usable=$usable table=${tableStructureSummary(structure)} text=\"${cameraLogSnippet(bestText)}\""
                            )

                            if (usable && scanGate.compareAndSet(true, false)) {
                                onResult(bestText, structure)
                            } else {
                                Log.w(
                                    CAMERA_TAG,
                                    "OCR rejected low-quality result or gate closed: score=${bestResult.score}, len=${bestText.length}"
                                )
                                selectedBoxRef.set(null)
                            }
                            cleanupOnce()
                        },
                        onFailure = { enhancedError ->
                            Log.e(CAMERA_TAG, "Enhanced recognizer pair failed", enhancedError)
                            onTableStructure(rawStructure)
                            val fallbackText = rawResult.normalizedText.trim()
                            val fallbackUsable = isOcrTextUsable(fallbackText, rawResult.score)
                            if (fallbackUsable && scanGate.compareAndSet(true, false)) {
                                Log.i(
                                    CAMERA_TAG,
                                    "OCR fallback accepted raw source=${rawResult.source} score=${rawResult.score}"
                                )
                                onResult(fallbackText, rawStructure)
                            } else {
                                Log.w(
                                    CAMERA_TAG,
                                    "OCR fallback raw rejected: score=${rawResult.score}, len=${fallbackText.length}"
                                )
                                selectedBoxRef.set(null)
                            }
                            cleanupOnce()
                        }
                    )
                },
                onFailure = { rawError ->
                    Log.e(CAMERA_TAG, "Raw recognizer pair failed", rawError)
                    runRecognizerPair(
                        input = enhancedInput,
                        sourceLabel = "enhanced_only",
                        chineseRecognizer = chineseRecognizer,
                        latinRecognizer = latinRecognizer,
                        onSuccess = { enhancedResult ->
                            val structure = buildTableStructure(
                                visionText = enhancedResult.vision,
                                roiWidth = enhancedForOcr.width,
                                roiHeight = enhancedForOcr.height
                            )
                            onTableStructure(structure)
                            val text = enhancedResult.normalizedText.trim()
                            val usable = isOcrTextUsable(text, enhancedResult.score)
                            Log.i(
                                CAMERA_TAG,
                                "OCR enhanced-only chosen=${enhancedResult.source} score=${enhancedResult.score} usable=$usable table=${tableStructureSummary(structure)} text=\"${cameraLogSnippet(text)}\""
                            )
                            if (usable && scanGate.compareAndSet(true, false)) {
                                onResult(text, structure)
                            } else {
                                selectedBoxRef.set(null)
                            }
                            cleanupOnce()
                        },
                        onFailure = { enhancedError ->
                            Log.e(CAMERA_TAG, "OCR failed on both raw/enhanced recognizer pairs", enhancedError)
                            selectedBoxRef.set(null)
                            cleanupOnce()
                        }
                    )
                }
            )
            return
        }

        ocrRunningRef.set(false)
        imageProxy.close()
        if (roiBitmap !== uprightBitmap && !roiBitmap.isRecycled) {
            roiBitmap.recycle()
        }
        if (uprightBitmap !== rawBitmap && !uprightBitmap.isRecycled) {
            uprightBitmap.recycle()
        }
        if (!rawBitmap.isRecycled) {
            rawBitmap.recycle()
        }
    } catch (e: Exception) {
        Log.e(CAMERA_TAG, "Frame processing failed", e)
        ocrRunningRef.set(false)
        imageProxy.close()
        if (roiBitmap != null && roiBitmap !== uprightBitmap && !roiBitmap.isRecycled) {
            roiBitmap.recycle()
        }
        if (uprightBitmap != null && uprightBitmap !== rawBitmap && !uprightBitmap.isRecycled) {
            uprightBitmap.recycle()
        }
        if (rawBitmap != null && !rawBitmap.isRecycled) {
            rawBitmap.recycle()
        }
    }
}

@Composable
fun RealtimeScannerScreen(
    cameraExecutor: ExecutorService,
    onTextDetected: (String, TableStructure?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val chineseRecognizer = remember {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    val latinRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val tableDetector = remember { YoloTableDetector(context) }

    var isScanning by remember { mutableStateOf(true) }
    val scanGate = remember { AtomicBoolean(true) }
    val selectedBoxRef = remember { AtomicReference<RectF?>(null) }
    val ocrRunningRef = remember { AtomicBoolean(false) }
    val frameCandidatesRef = remember { AtomicReference<List<FrameCandidate>>(emptyList()) }
    var scanLineOffset by remember { mutableFloatStateOf(0f) }
    var detectedBox by remember { mutableStateOf<RectF?>(null) }
    var overlayTable by remember { mutableStateOf<TableStructure?>(null) }
    var lastImageWidth by remember { mutableStateOf(0) }
    var lastImageHeight by remember { mutableStateOf(0) }
    val detectorStatus = remember(tableDetector) { tableDetector.statusMessage }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(
                            imageProxy = imageProxy,
                            detector = tableDetector,
                            chineseRecognizer = chineseRecognizer,
                            latinRecognizer = latinRecognizer,
                            scanGate = scanGate,
                            selectedBoxRef = selectedBoxRef,
                            ocrRunningRef = ocrRunningRef,
                            frameCandidatesRef = frameCandidatesRef,
                            onBoxDetected = { box, imageW, imageH ->
                                ContextCompat.getMainExecutor(context).execute {
                                    lastImageWidth = imageW
                                    lastImageHeight = imageH
                                    if (selectedBoxRef.get() == null) {
                                        detectedBox = box
                                    }
                                }
                            },
                            onTableStructure = { table ->
                                ContextCompat.getMainExecutor(context).execute {
                                    overlayTable = table
                                }
                            },
                            onResult = { detectedText, tableStructure ->
                                ContextCompat.getMainExecutor(context).execute {
                                    isScanning = false
                                }
                                onTextDetected(detectedText, tableStructure)
                            }
                        )
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(CAMERA_TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            while (true) {
                delay(16)
                scanLineOffset = (scanLineOffset + 2f) % SCAN_FRAME_SIZE_DP
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            frameCandidatesRef.getAndSet(emptyList()).forEach {
                if (!it.bitmap.isRecycled) {
                    it.bitmap.recycle()
                }
            }
            chineseRecognizer.close()
            latinRecognizer.close()
            tableDetector.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val box = detectedBox
            if (box != null) {
                if (lastImageWidth <= 0 || lastImageHeight <= 0) return@Canvas
                val viewRect = imageNormRectToViewRect(
                    rectNorm = box,
                    imageWidth = lastImageWidth,
                    imageHeight = lastImageHeight,
                    viewWidth = size.width,
                    viewHeight = size.height
                )
                val left = viewRect.left
                val top = viewRect.top
                val right = viewRect.right
                val bottom = viewRect.bottom

                val w = (right - left).coerceAtLeast(0f)
                val h = (bottom - top).coerceAtLeast(0f)

                if (w > 0f && h > 0f) {
                    // Dim outside of the detected box.
                    drawRect(
                        color = Color.Black.copy(alpha = 0.48f),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, top.coerceAtLeast(0f))
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.48f),
                        topLeft = Offset(0f, bottom.coerceAtMost(size.height)),
                        size = Size(
                            size.width,
                            (size.height - bottom).coerceAtLeast(0f)
                        )
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.48f),
                        topLeft = Offset(0f, top),
                        size = Size(left.coerceAtLeast(0f), h)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.48f),
                        topLeft = Offset(right.coerceAtMost(size.width), top),
                        size = Size((size.width - right).coerceAtLeast(0f), h)
                    )

                    drawRect(
                        color = Color(0xFF00E676),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 4f)
                    )

                    val table = overlayTable
                    if (table != null && table.rowBoundariesNorm.size >= 2 && table.colBoundariesNorm.size >= 2) {
                        val lineColor = Color(0x9900E5FF)
                        val lineStroke = 2f

                        for (i in 1 until table.rowBoundariesNorm.lastIndex) {
                            val ry = table.rowBoundariesNorm[i].coerceIn(0f, 1f)
                            val yLine = top + (h * ry)
                            drawLine(
                                color = lineColor,
                                start = Offset(left, yLine),
                                end = Offset(right, yLine),
                                strokeWidth = lineStroke
                            )
                        }
                        for (i in 1 until table.colBoundariesNorm.lastIndex) {
                            val rx = table.colBoundariesNorm[i].coerceIn(0f, 1f)
                            val xLine = left + (w * rx)
                            drawLine(
                                color = lineColor,
                                start = Offset(xLine, top),
                                end = Offset(xLine, bottom),
                                strokeWidth = lineStroke
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Scan Nutrition Table",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(48.dp))
        }

        Box(
            modifier = Modifier
                .size(SCAN_FRAME_SIZE_DP.dp)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 4f
                val cornerLength = 40f

                drawLine(Color(0xFF4CAF50), Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth)
                drawLine(Color(0xFF4CAF50), Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth)

                drawLine(
                    Color(0xFF4CAF50),
                    Offset(size.width, 0f),
                    Offset(size.width - cornerLength, 0f),
                    strokeWidth
                )
                drawLine(
                    Color(0xFF4CAF50),
                    Offset(size.width, 0f),
                    Offset(size.width, cornerLength),
                    strokeWidth
                )

                drawLine(
                    Color(0xFF4CAF50),
                    Offset(0f, size.height),
                    Offset(cornerLength, size.height),
                    strokeWidth
                )
                drawLine(
                    Color(0xFF4CAF50),
                    Offset(0f, size.height),
                    Offset(0f, size.height - cornerLength),
                    strokeWidth
                )

                drawLine(
                    Color(0xFF4CAF50),
                    Offset(size.width, size.height),
                    Offset(size.width - cornerLength, size.height),
                    strokeWidth
                )
                drawLine(
                    Color(0xFF4CAF50),
                    Offset(size.width, size.height),
                    Offset(size.width, size.height - cornerLength),
                    strokeWidth
                )
            }

            if (isScanning) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .offset(y = scanLineOffset.dp - (SCAN_FRAME_SIZE_DP / 2f).dp)
                ) {
                    drawLine(
                        color = Color(0xFF4CAF50),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }
            }

            Text(
                text = "Align nutrition table in frame",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = (SCAN_FRAME_SIZE_DP + 20f).dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isScanning) "Detecting table..." else "Text detected",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (tableDetector.isInitialized) {
                            "YOLO detect -> auto OCR (ZH + Latin)"
                        } else {
                            "YOLO unavailable"
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = detectorStatus,
                        color = if (tableDetector.isInitialized) {
                            Color(0xFFB9F6CA)
                        } else {
                            Color(0xFFFFCDD2)
                        },
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}


