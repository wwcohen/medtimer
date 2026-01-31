package com.medtimer.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

class BellPlayer(private val context: Context) {
    private var intervalBellPlayer: MediaPlayer? = null
    private var finalBellPlayer: MediaPlayer? = null

    // Two players for gapless white noise looping
    private var whiteNoisePlayerA: MediaPlayer? = null
    private var whiteNoisePlayerB: MediaPlayer? = null
    private var currentWhiteNoiseVolume: Float = 0f
    private var isWhiteNoisePlaying = false
    private var usePlayerA = true
    private val handler = Handler(Looper.getMainLooper())
    private var loopRunnable: Runnable? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    // Get resource ID by name, returns 0 if not found
    private fun getRawResourceId(name: String): Int {
        return context.resources.getIdentifier(name, "raw", context.packageName)
    }

    private fun getDefaultNotificationUri(): Uri? {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun playIntervalBell() {
        try {
            intervalBellPlayer?.release()

            val resId = getRawResourceId("interval_bell")
            intervalBellPlayer = if (resId != 0) {
                MediaPlayer.create(context, resId)
            } else {
                getDefaultNotificationUri()?.let { uri ->
                    MediaPlayer.create(context, uri)
                }
            }

            intervalBellPlayer?.apply {
                setAudioAttributes(audioAttributes)
                setVolume(1.0f, 1.0f)
                setOnCompletionListener { mp ->
                    mp.release()
                    if (intervalBellPlayer == mp) {
                        intervalBellPlayer = null
                    }
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playFinalBell() {
        try {
            finalBellPlayer?.release()

            val resId = getRawResourceId("final_bell")
            finalBellPlayer = if (resId != 0) {
                MediaPlayer.create(context, resId)
            } else {
                getDefaultNotificationUri()?.let { uri ->
                    MediaPlayer.create(context, uri)
                }
            }

            finalBellPlayer?.apply {
                setAudioAttributes(audioAttributes)
                setVolume(1.0f, 1.0f)
                setOnCompletionListener { mp ->
                    mp.release()
                    if (finalBellPlayer == mp) {
                        finalBellPlayer = null
                    }
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startWhiteNoise(volume: Float) {
        if (volume <= 0f) return

        currentWhiteNoiseVolume = volume
        isWhiteNoisePlaying = true

        try {
            stopWhiteNoise()
            isWhiteNoisePlaying = true
            usePlayerA = true

            val resId = getRawResourceId("white_noise")
            if (resId != 0) {
                // Create both players upfront
                whiteNoisePlayerA = MediaPlayer.create(context, resId)?.apply {
                    setAudioAttributes(audioAttributes)
                    setVolume(volume, volume)
                }
                whiteNoisePlayerB = MediaPlayer.create(context, resId)?.apply {
                    setAudioAttributes(audioAttributes)
                    setVolume(volume, volume)
                }

                // Start first player
                whiteNoisePlayerA?.start()

                // Get duration and schedule the loop
                val duration = whiteNoisePlayerA?.duration ?: 10000
                scheduleNextLoop(duration)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleNextLoop(durationMs: Int) {
        // Start next player 100ms before current one ends for overlap
        val overlapMs = 100L
        val delayMs = (durationMs - overlapMs).coerceAtLeast(100)

        loopRunnable = Runnable {
            if (isWhiteNoisePlaying) {
                try {
                    val resId = getRawResourceId("white_noise")
                    if (resId == 0) return@Runnable

                    if (usePlayerA) {
                        // Player A is ending, start B
                        whiteNoisePlayerB?.release()
                        whiteNoisePlayerB = MediaPlayer.create(context, resId)?.apply {
                            setAudioAttributes(audioAttributes)
                            setVolume(currentWhiteNoiseVolume, currentWhiteNoiseVolume)
                            start()
                        }
                        // Release A after a brief delay
                        handler.postDelayed({
                            whiteNoisePlayerA?.release()
                            whiteNoisePlayerA = MediaPlayer.create(context, resId)?.apply {
                                setAudioAttributes(audioAttributes)
                                setVolume(currentWhiteNoiseVolume, currentWhiteNoiseVolume)
                            }
                        }, overlapMs + 50)
                    } else {
                        // Player B is ending, start A
                        whiteNoisePlayerA?.release()
                        whiteNoisePlayerA = MediaPlayer.create(context, resId)?.apply {
                            setAudioAttributes(audioAttributes)
                            setVolume(currentWhiteNoiseVolume, currentWhiteNoiseVolume)
                            start()
                        }
                        // Release B after a brief delay
                        handler.postDelayed({
                            whiteNoisePlayerB?.release()
                            whiteNoisePlayerB = MediaPlayer.create(context, resId)?.apply {
                                setAudioAttributes(audioAttributes)
                                setVolume(currentWhiteNoiseVolume, currentWhiteNoiseVolume)
                            }
                        }, overlapMs + 50)
                    }
                    usePlayerA = !usePlayerA

                    // Schedule next loop
                    scheduleNextLoop(durationMs)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        handler.postDelayed(loopRunnable!!, delayMs)
    }

    fun setWhiteNoiseVolume(volume: Float) {
        currentWhiteNoiseVolume = volume
        whiteNoisePlayerA?.setVolume(volume, volume)
        whiteNoisePlayerB?.setVolume(volume, volume)
    }

    fun stopWhiteNoise() {
        isWhiteNoisePlaying = false
        loopRunnable?.let { handler.removeCallbacks(it) }
        loopRunnable = null
        handler.removeCallbacksAndMessages(null)

        whiteNoisePlayerA?.apply {
            try { stop() } catch (e: Exception) {}
            release()
        }
        whiteNoisePlayerA = null

        whiteNoisePlayerB?.apply {
            try { stop() } catch (e: Exception) {}
            release()
        }
        whiteNoisePlayerB = null
    }

    fun release() {
        intervalBellPlayer?.release()
        intervalBellPlayer = null
        finalBellPlayer?.release()
        finalBellPlayer = null
        stopWhiteNoise()
    }
}
