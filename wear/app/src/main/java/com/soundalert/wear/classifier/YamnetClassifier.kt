package com.soundalert.wear.classifier

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * YAMNet (clasificación, TFLite) sobre LiteRT, en CPU con XNNPACK y 1 hilo
 * (prioridad: batería, no velocidad). Se usa la API Interpreter de LiteRT
 * porque solo necesitamos CPU y permite verificar los tensores del modelo.
 *
 * Entrada: 15 600 muestras float [-1, 1] (0,975 s a 16 kHz).
 * Salida: 521 puntuaciones (clases de yamnet_class_map.csv).
 *
 * No es thread-safe: se usa desde un único hilo de inferencia.
 */
class YamnetClassifier(context: Context, threads: Int = 1) : SoundClassifier {

    override val info = ClassifierInfo(name = "yamnet-litert", version = "classification-tflite-1")

    private val interpreter: Interpreter
    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer
    private val outputClasses: Int

    override val inputSamples: Int

    /** Duración de la última inferencia (solo `run`), en ms. */
    var lastInferenceMs: Double = 0.0
        private set

    init {
        val model = loadModel(context, MODEL_ASSET)
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true))

        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        check(interpreter.inputTensorCount == 1) { "Se esperaba 1 entrada, hay ${interpreter.inputTensorCount}" }
        check(input.dataType() == DataType.FLOAT32 && output.dataType() == DataType.FLOAT32) {
            "Tipos inesperados: ${input.dataType()} → ${output.dataType()}"
        }
        inputSamples = input.shape().fold(1) { acc, d -> acc * d }
        outputClasses = output.shape().last()
        check(inputSamples == EXPECTED_SAMPLES) { "Entrada inesperada ${input.shape().contentToString()}" }
        check(outputClasses == EXPECTED_CLASSES) { "Salida inesperada ${output.shape().contentToString()}" }

        inputBuffer = ByteBuffer.allocateDirect(input.numBytes()).order(ByteOrder.nativeOrder())
        outputBuffer = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())

        Log.i(
            TAG,
            "Modelo cargado: entrada ${input.name()} ${input.shape().contentToString()}, " +
                "salida ${output.name()} ${output.shape().contentToString()}, " +
                "${interpreter.outputTensorCount} salida(s), threads=$threads",
        )
    }

    override fun classify(waveform: FloatArray): FloatArray {
        require(waveform.size == inputSamples) { "Se esperaban $inputSamples muestras, llegaron ${waveform.size}" }
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(waveform)
        outputBuffer.rewind()

        val start = System.nanoTime()
        interpreter.run(inputBuffer, outputBuffer)
        lastInferenceMs = (System.nanoTime() - start) / 1e6

        outputBuffer.rewind()
        return FloatArray(outputClasses).also { outputBuffer.asFloatBuffer().get(it) }
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_ASSET = "yamnet.tflite"
        const val CLASS_MAP_ASSET = "yamnet_class_map.csv"
        const val EXPECTED_SAMPLES = 15_600
        const val EXPECTED_CLASSES = 521
        private const val TAG = "SA/YAMNet"

        /** El asset no está comprimido (noCompress "tflite"), así que se mapea sin copiarlo. */
        private fun loadModel(context: Context, asset: String): MappedByteBuffer =
            context.assets.openFd(asset).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }

        fun loadLabelMapper(context: Context): LabelMapper =
            LabelMapper(context.assets.open(CLASS_MAP_ASSET).reader().use(LabelMapper::parseClassMap))
    }
}
