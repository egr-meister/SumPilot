package com.sumpilot.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.sumpilot.R

/**
 * Short bundled feedback sounds. Only played in response to an on-screen answer (so only while
 * the app is visible), through the media stream so device volume and silent settings apply.
 * Loaded lazily the first time sound is enabled; never plays in the background.
 */
class SoundPlayer(private val context: Context) {
    private var pool: SoundPool? = null
    private var correctId = 0
    private var gentleId = 0
    private val loaded = HashSet<Int>()

    private fun ensureLoaded(): SoundPool {
        pool?.let { return it }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        correctId = p.load(context, R.raw.feedback_correct, 1)
        gentleId = p.load(context, R.raw.feedback_gentle, 1)
        pool = p
        return p
    }

    fun preload() {
        ensureLoaded()
    }

    fun playCorrect() = play { correctId }

    fun playGentle() = play { gentleId }

    private inline fun play(id: () -> Int) {
        val p = ensureLoaded()
        val sample = id()
        if (sample in loaded) p.play(sample, 0.6f, 0.6f, 1, 0, 1f)
    }

    fun release() {
        pool?.release()
        pool = null
        loaded.clear()
    }
}
