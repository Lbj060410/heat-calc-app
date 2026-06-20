package com.example.connects.features.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

private const val DETECTOR_TAG = "YoloTableDetector"
private const val DEFAULT_MODEL_ASSET = "yingyang.tflite"

data class TableDetection(
    val rect: RectF,
    val score: Float
)

private data class LetterboxResult(
    val bitmap: Bitmap,
    val scale: Float,
    val padX: Float,
    val padY: Float
)

private data class TensorSpec(
    val index: Int,
    val shape: IntArray,
    val dataType: DataType,
    val scale: Float,
    val zeroPoint: Int
)

class YoloTableDetector(
    context: Context,
    private val modelAssetName: String = DEFAULT_MODEL_ASSET,
    private val confidenceThreshold: Float = 0.15f
) : Closeable {

    private val interpreter: Interpreter?
    private val inputDataType: DataType
    private val inputScale: Float
    private val inputZeroPoint: Int
    private val outputDataType: DataType
    private val outputScale: Float
    private val outputZeroPoint: Int
    private val inputWidth: Int
    private val inputHeight: Int
    private val inputChannels: Int
    private val inputNchw: Boolean
    private val outputTensorSpecs: List<TensorSpec>
    private val initErrorMessage: String?

    val isInitialized: Boolean
        get() = interpreter != null && initErrorMessage == null

    val statusMessage: String
        get() = if (isInitialized) {
            val outputs = outputTensorSpecs.joinToString(" | ") {
                "#${it.index}${it.shape.contentToString()}/${it.dataType}"
            }
            "YOLO ready: $modelAssetName, $inputDataType, ${inputWidth}x$inputHeight, out=$outputs"
        } else {
            "YOLO init failed: ${initErrorMessage ?: "unknown"}"
        }

    init {
        var createdInterpreter: Interpreter? = null
        var initError: String? = null

        var inType = DataType.FLOAT32
        var inScale = 0f
        var inZeroPoint = 0
        var outType = DataType.FLOAT32
        var outScale = 0f
        var outZeroPoint = 0
        val outSpecs = mutableListOf<TensorSpec>()

        var width = 640
        var height = 640
        var channels = 3
        var nchw = false

        try {
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val modelBuffer = loadModelBuffer(context, modelAssetName)
            createdInterpreter = Interpreter(modelBuffer, options)

            val inputTensor = createdInterpreter.getInputTensor(0)
            val inputShape = inputTensor.shape()
            inType = inputTensor.dataType()
            val inputQuant = inputTensor.quantizationParams()
            inScale = inputQuant.scale
            inZeroPoint = inputQuant.zeroPoint

            for (index in 0 until createdInterpreter.outputTensorCount) {
                val outputTensor = createdInterpreter.getOutputTensor(index)
                val outputQuant = outputTensor.quantizationParams()
                outSpecs += TensorSpec(
                    index = index,
                    shape = outputTensor.shape(),
                    dataType = outputTensor.dataType(),
                    scale = outputQuant.scale,
                    zeroPoint = outputQuant.zeroPoint
                )
            }
            if (outSpecs.isNotEmpty()) {
                outType = outSpecs[0].dataType
                outScale = outSpecs[0].scale
                outZeroPoint = outSpecs[0].zeroPoint
            }

            // Common input layout: NHWC [1,H,W,3] or NCHW [1,3,H,W]
            if (inputShape.size == 4 && inputShape[1] == 3 && inputShape[3] != 3) {
                nchw = true
                channels = inputShape[1]
                height = inputShape[2]
                width = inputShape[3]
            } else if (inputShape.size == 4) {
                nchw = false
                height = inputShape[1]
                width = inputShape[2]
                channels = inputShape[3]
            }

            Log.i(
                DETECTOR_TAG,
                "Model initialized. input=${inputShape.contentToString()} type=$inType, " +
                    "outputs=${outSpecs.joinToString { "#${it.index}${it.shape.contentToString()}/${it.dataType}" }}"
            )
        } catch (e: Exception) {
            initError = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
            Log.e(DETECTOR_TAG, "Failed to init YOLO detector", e)
        }

        interpreter = createdInterpreter
        inputDataType = inType
        inputScale = inScale
        inputZeroPoint = inZeroPoint
        outputDataType = outType
        outputScale = outScale
        outputZeroPoint = outZeroPoint
        inputWidth = width
        inputHeight = height
        inputChannels = channels
        inputNchw = nchw
        outputTensorSpecs = outSpecs.toList()
        initErrorMessage = initError
    }

    fun detect(bitmap: Bitmap): TableDetection? {
        val localInterpreter = interpreter ?: return null
        if (inputChannels < 3) return null

        var preprocessed: Bitmap? = null
        return try {
            val letterboxed = letterbox(bitmap, inputWidth, inputHeight)
            preprocessed = letterboxed.bitmap
            val inputBuffer = createInputBuffer(preprocessed)
            val outputs = runInferenceAll(localInterpreter, inputBuffer)
            val best = decodeBestDetection(outputs) ?: return null

            // Undo letterbox and map model coordinates back to original bitmap.
            val mapped = RectF(
                (best.rect.left - letterboxed.padX) / letterboxed.scale,
                (best.rect.top - letterboxed.padY) / letterboxed.scale,
                (best.rect.right - letterboxed.padX) / letterboxed.scale,
                (best.rect.bottom - letterboxed.padY) / letterboxed.scale
            )

            val clamped = RectF(
                mapped.left.coerceIn(0f, bitmap.width.toFloat() - 1f),
                mapped.top.coerceIn(0f, bitmap.height.toFloat() - 1f),
                mapped.right.coerceIn(1f, bitmap.width.toFloat()),
                mapped.bottom.coerceIn(1f, bitmap.height.toFloat())
            )

            if (clamped.width() < 4f || clamped.height() < 4f) {
                null
            } else {
                TableDetection(rect = clamped, score = best.score)
            }
        } catch (e: Exception) {
            Log.e(DETECTOR_TAG, "Detection failed", e)
            null
        } finally {
            if (preprocessed != null && preprocessed !== bitmap && !preprocessed.isRecycled) {
                preprocessed.recycle()
            }
        }
    }

    private fun letterbox(src: Bitmap, dstWidth: Int, dstHeight: Int): LetterboxResult {
        val srcW = src.width.toFloat().coerceAtLeast(1f)
        val srcH = src.height.toFloat().coerceAtLeast(1f)
        val scale = min(dstWidth / srcW, dstHeight / srcH)

        val resizedW = (srcW * scale).roundToInt().coerceAtLeast(1)
        val resizedH = (srcH * scale).roundToInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(src, resizedW, resizedH, true)

        val output = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(114, 114, 114))

        val padX = ((dstWidth - resizedW) / 2f).coerceAtLeast(0f)
        val padY = ((dstHeight - resizedH) / 2f).coerceAtLeast(0f)
        canvas.drawBitmap(resized, padX, padY, Paint(Paint.FILTER_BITMAP_FLAG))

        if (!resized.isRecycled) {
            resized.recycle()
        }

        return LetterboxResult(
            bitmap = output,
            scale = scale,
            padX = padX,
            padY = padY
        )
    }

    private fun runInferenceAll(
        localInterpreter: Interpreter,
        inputBuffer: ByteBuffer
    ): Map<Int, FloatArray> {
        if (outputTensorSpecs.isEmpty()) return emptyMap()

        val outputBuffers = mutableMapOf<Int, ByteBuffer>()
        val outputMap = mutableMapOf<Int, Any>()

        for (spec in outputTensorSpecs) {
            val count = spec.shape.fold(1) { acc, d -> acc * d }
            val bytesPerElement = when (spec.dataType) {
                DataType.FLOAT32 -> 4
                DataType.UINT8, DataType.INT8 -> 1
                else -> 4
            }
            val outputBuffer = ByteBuffer
                .allocateDirect(count * bytesPerElement)
                .order(ByteOrder.nativeOrder())
            outputBuffers[spec.index] = outputBuffer
            outputMap[spec.index] = outputBuffer
        }

        inputBuffer.rewind()
        localInterpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

        val decoded = mutableMapOf<Int, FloatArray>()
        for (spec in outputTensorSpecs) {
            val buffer = outputBuffers.getValue(spec.index)
            val count = spec.shape.fold(1) { acc, d -> acc * d }
            decoded[spec.index] = decodeOutputBuffer(spec, buffer, count)
        }
        return decoded
    }

    private fun decodeOutputBuffer(spec: TensorSpec, buffer: ByteBuffer, count: Int): FloatArray {
        buffer.rewind()
        return when (spec.dataType) {
            DataType.FLOAT32 -> FloatArray(count).also {
                buffer.asFloatBuffer().get(it)
            }

            DataType.UINT8, DataType.INT8 -> {
                val bytes = ByteArray(count)
                buffer.get(bytes)
                FloatArray(count) { i ->
                    val raw = if (spec.dataType == DataType.UINT8) {
                        bytes[i].toInt() and 0xFF
                    } else {
                        bytes[i].toInt()
                    }
                    decodeQuantizedValue(raw, spec.scale, spec.zeroPoint)
                }
            }

            else -> FloatArray(count).also {
                buffer.asFloatBuffer().get(it)
            }
        }
    }

    private fun decodeQuantizedValue(raw: Int, scale: Float, zeroPoint: Int): Float {
        return if (scale == 0f) raw.toFloat() else (raw - zeroPoint) * scale
    }

    private fun createInputBuffer(bitmap: Bitmap): ByteBuffer {
        val pixelCount = inputWidth * inputHeight
        val capacity = when (inputDataType) {
            DataType.FLOAT32 -> pixelCount * inputChannels * 4
            DataType.UINT8, DataType.INT8 -> pixelCount * inputChannels
            else -> pixelCount * inputChannels * 4
        }

        val buffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        if (inputDataType == DataType.FLOAT32) {
            if (inputNchw) {
                // CHW
                for (c in 0 until 3) {
                    for (i in pixels.indices) {
                        val p = pixels[i]
                        val value = when (c) {
                            0 -> ((p shr 16) and 0xFF) / 255f
                            1 -> ((p shr 8) and 0xFF) / 255f
                            else -> (p and 0xFF) / 255f
                        }
                        buffer.putFloat(value)
                    }
                }
            } else {
                // HWC
                for (p in pixels) {
                    buffer.putFloat(((p shr 16) and 0xFF) / 255f)
                    buffer.putFloat(((p shr 8) and 0xFF) / 255f)
                    buffer.putFloat((p and 0xFF) / 255f)
                }
            }
        } else {
            // Quantized input path (UINT8/INT8).
            if (inputNchw) {
                for (c in 0 until 3) {
                    for (i in pixels.indices) {
                        val p = pixels[i]
                        val normalized = when (c) {
                            0 -> ((p shr 16) and 0xFF) / 255f
                            1 -> ((p shr 8) and 0xFF) / 255f
                            else -> (p and 0xFF) / 255f
                        }
                        buffer.put(encodeQuantizedValue(normalized))
                    }
                }
            } else {
                for (p in pixels) {
                    buffer.put(encodeQuantizedValue(((p shr 16) and 0xFF) / 255f))
                    buffer.put(encodeQuantizedValue(((p shr 8) and 0xFF) / 255f))
                    buffer.put(encodeQuantizedValue((p and 0xFF) / 255f))
                }
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun encodeQuantizedValue(normalized: Float): Byte {
        if (inputScale == 0f) {
            val fallback = (normalized * 255f).roundToInt().coerceIn(0, 255)
            return fallback.toByte()
        }
        val maxValue = if (inputDataType == DataType.INT8) 127 else 255
        val minValue = if (inputDataType == DataType.INT8) -128 else 0
        val quantized = (normalized / inputScale + inputZeroPoint)
            .roundToInt()
            .coerceIn(minValue, maxValue)
        return quantized.toByte()
    }

    private fun decodeBestDetection(outputs: Map<Int, FloatArray>): TableDetection? {
        if (outputs.isEmpty() || outputTensorSpecs.isEmpty()) return null

        if (outputTensorSpecs.size == 1) {
            val spec = outputTensorSpecs[0]
            val data = outputs[spec.index] ?: return null
            return decodeSingleTensor(spec.shape, data)
        }

        decodeSplitBoxesScores(outputs)?.let { return it }

        // Fallback: try each tensor as if it were a single-output YOLO tensor.
        for (spec in outputTensorSpecs) {
            val data = outputs[spec.index] ?: continue
            decodeSingleTensor(spec.shape, data)?.let { return it }
        }

        Log.w(
            DETECTOR_TAG,
            "Failed to decode outputs: ${outputTensorSpecs.joinToString { "#${it.index}${it.shape.contentToString()}" }}"
        )
        return null
    }

    private fun decodeSingleTensor(shape: IntArray, output: FloatArray): TableDetection? {
        if (shape.size != 3 || shape[0] != 1) {
            Log.w(DETECTOR_TAG, "Unsupported output shape: ${shape.contentToString()}")
            return null
        }

        val dim1 = shape[1]
        val dim2 = shape[2]
        var bestScore = 0f
        var bestRect: RectF? = null

        if (dim1 in 4..512 && dim2 >= 16) {
            // channels-first style: [1, attrs, candidates]
            val attrs = dim1
            val candidates = dim2
            for (i in 0 until candidates) {
                val x0 = output[0 * dim1 * dim2 + 0 * dim2 + i]
                val y0 = output[0 * dim1 * dim2 + 1 * dim2 + i]
                val x1 = output[0 * dim1 * dim2 + 2 * dim2 + i]
                val y1 = output[0 * dim1 * dim2 + 3 * dim2 + i]
                val score = scoreFromAttrs(output, attrs, i) { c ->
                    output[0 * dim1 * dim2 + c * dim2 + i]
                }
                if (score < confidenceThreshold || score <= bestScore) continue
                val rect = normalizeIfNeeded(rectFromFourValues(x0, y0, x1, y1))
                if (rect.width() > 0f && rect.height() > 0f) {
                    bestScore = score
                    bestRect = rect
                }
            }
        } else if (dim1 >= 16 && dim2 in 5..512) {
            // candidates-first style: [1, candidates, attrs]
            val candidates = dim1
            val attrs = dim2
            for (i in 0 until candidates) {
                val base = i * attrs
                val x0 = output[base + 0]
                val y0 = output[base + 1]
                val x1 = output[base + 2]
                val y1 = output[base + 3]
                val score = scoreFromAttrs(output, attrs, i) { c -> output[base + c] }
                if (score < confidenceThreshold || score <= bestScore) continue
                val rect = normalizeIfNeeded(rectFromFourValues(x0, y0, x1, y1))
                if (rect.width() > 0f && rect.height() > 0f) {
                    bestScore = score
                    bestRect = rect
                }
            }
        } else {
            Log.w(DETECTOR_TAG, "Unhandled output dimensions: ${shape.contentToString()}")
        }

        return bestRect?.let { TableDetection(it, bestScore) }
    }

    private fun decodeSplitBoxesScores(outputs: Map<Int, FloatArray>): TableDetection? {
        val boxesSpec = outputTensorSpecs.firstOrNull {
            it.shape.size == 3 && it.shape[0] == 1 && (it.shape[1] == 4 || it.shape[2] == 4)
        } ?: return null
        val scoresSpec = outputTensorSpecs.firstOrNull { it.index != boxesSpec.index && it.shape.size == 3 && it.shape[0] == 1 }
            ?: return null

        val boxes = outputs[boxesSpec.index] ?: return null
        val scores = outputs[scoresSpec.index] ?: return null

        val boxCandidates = if (boxesSpec.shape[1] == 4) boxesSpec.shape[2] else boxesSpec.shape[1]
        val scoreCandidates = if (scoresSpec.shape[1] >= scoresSpec.shape[2]) scoresSpec.shape[1] else scoresSpec.shape[2]
        val candidates = min(boxCandidates, scoreCandidates)
        if (candidates <= 0) return null

        var bestScore = 0f
        var bestRect: RectF? = null

        for (i in 0 until candidates) {
            val score = scoreFromSplitTensor(scores, scoresSpec.shape, i)
            if (score < confidenceThreshold || score <= bestScore) continue

            val (x0, y0, x1, y1) = readFourFromBoxes(boxes, boxesSpec.shape, i) ?: continue
            val rect = normalizeIfNeeded(rectFromFourValues(x0, y0, x1, y1))
            if (rect.width() > 0f && rect.height() > 0f) {
                bestScore = score
                bestRect = rect
            }
        }

        return bestRect?.let { TableDetection(it, bestScore) }
    }

    private fun scoreFromAttrs(
        output: FloatArray,
        attrs: Int,
        candidateIndex: Int,
        getter: (Int) -> Float
    ): Float {
        if (attrs <= 4) return 0f
        if (attrs == 5) return toProbability(getter(4))
        if (attrs == 6) {
            // Compatible with end-to-end exports (e.g. YOLO26 [x1,y1,x2,y2,score,class_id])
            // and also reasonable for 1-class legacy heads where attr[5] is class confidence.
            return toProbability(getter(4))
        }
        val obj = toProbability(getter(4))
        var clsMax = 0f
        for (c in 5 until attrs) {
            val v = toProbability(getter(c))
            if (v > clsMax) clsMax = v
        }
        // For modern heads without explicit objectness, clsMax usually already reflects confidence.
        // For legacy heads with objectness, obj*clsMax is correct.
        return max(clsMax, obj * clsMax)
    }

    private fun scoreFromSplitTensor(scores: FloatArray, shape: IntArray, candidate: Int): Float {
        val dim1 = shape[1]
        val dim2 = shape[2]

        if (dim1 == 1) {
            return toProbability(scores[candidate])
        }
        if (dim2 == 1) {
            return toProbability(scores[candidate * dim2])
        }

        var best = 0f
        if (dim1 < dim2) {
            // [1, classes, candidates]
            for (c in 0 until dim1) {
                val v = toProbability(scores[c * dim2 + candidate])
                if (v > best) best = v
            }
        } else {
            // [1, candidates, classes]
            val base = candidate * dim2
            for (c in 0 until dim2) {
                val v = toProbability(scores[base + c])
                if (v > best) best = v
            }
        }
        return best
    }

    private fun readFourFromBoxes(
        boxes: FloatArray,
        shape: IntArray,
        candidate: Int
    ): FloatArray? {
        val dim1 = shape[1]
        val dim2 = shape[2]
        return if (dim1 == 4) {
            floatArrayOf(
                boxes[0 * dim2 + candidate],
                boxes[1 * dim2 + candidate],
                boxes[2 * dim2 + candidate],
                boxes[3 * dim2 + candidate]
            )
        } else if (dim2 == 4) {
            val base = candidate * dim2
            floatArrayOf(
                boxes[base + 0],
                boxes[base + 1],
                boxes[base + 2],
                boxes[base + 3]
            )
        } else {
            null
        }
    }

    private fun rectFromFourValues(v0: Float, v1: Float, v2: Float, v3: Float): RectF {
        return if (v2 > v0 && v3 > v1) {
            RectF(min(v0, v2), min(v1, v3), max(v0, v2), max(v1, v3))
        } else {
            RectF(v0 - v2 / 2f, v1 - v3 / 2f, v0 + v2 / 2f, v1 + v3 / 2f)
        }
    }

    private fun normalizeIfNeeded(rect: RectF): RectF {
        val maxAbs = max(
            max(abs(rect.left), abs(rect.top)),
            max(abs(rect.right), abs(rect.bottom))
        )
        return if (maxAbs <= 1.5f) {
            RectF(
                rect.left * inputWidth,
                rect.top * inputHeight,
                rect.right * inputWidth,
                rect.bottom * inputHeight
            )
        } else {
            rect
        }
    }

    private fun toProbability(raw: Float): Float {
        return if (raw in 0f..1f) raw else (1f / (1f + exp(-raw)))
    }

    private fun loadModelBuffer(context: Context, assetName: String): ByteBuffer {
        return try {
            val fd = context.assets.openFd(assetName)
            FileInputStream(fd.fileDescriptor).use { inputStream ->
                inputStream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                )
            }
        } catch (e: Exception) {
            Log.w(
                DETECTOR_TAG,
                "openFd failed for $assetName, fallback to stream buffer: ${e.message}"
            )
            context.assets.open(assetName).use { input ->
                val bytes = input.readBytes()
                ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                    put(bytes)
                    rewind()
                }
            }
        }
    }

    override fun close() {
        interpreter?.close()
    }
}
