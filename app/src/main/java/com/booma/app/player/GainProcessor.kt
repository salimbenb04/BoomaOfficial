package com.booma.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Changes the volume of the decoded audio by a set number of millibels (100 mB = 1 dB).
 * Used for volume normalisation: loud songs are turned down to the same level as the rest.
 * (Same approach as Metrolist's normalisation processor.)
 */
@OptIn(UnstableApi::class)
class GainProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0

    @Volatile
    private var linear = 1.0

    @Volatile
    var gainMb: Int = 0
        private set

    fun setGain(mb: Int) {
        gainMb = mb
        linear = 10.0.pow(mb / 2000.0)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> 0
        }
        // other formats pass through untouched (the processor switches itself off)
        return if (bytesPerSample == 0) AudioProcessor.AudioFormat.NOT_SET else inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val g = linear
        val samples = inputBuffer.remaining() / bytesPerSample
        val output = replaceOutputBuffer(samples * bytesPerSample)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        if (encoding == C.ENCODING_PCM_16BIT) {
            repeat(samples) {
                val s = inputBuffer.getShort()
                output.putShort(if (g == 1.0) s else (s * g).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        } else {
            val gf = g.toFloat()
            repeat(samples) {
                val s = inputBuffer.getFloat()
                output.putFloat(if (g == 1.0) s else (s * gf).coerceIn(-1f, 1f))
            }
        }
        output.flip()
    }

    override fun onReset() {
        encoding = C.ENCODING_INVALID
        bytesPerSample = 0
    }
}
