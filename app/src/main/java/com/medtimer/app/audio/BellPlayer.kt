package com.medtimer.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

class BellPlayer(private val context: Context) {
    private var intervalBellPlayer: MediaPlayer? = null
    private var finalBellPlayer: MediaPlayer? = null

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
                // Fall back to system notification sound
                getDefaultNotificationUri()?.let { uri ->
                    MediaPlayer.create(context, uri)
                }
            }

            intervalBellPlayer?.apply {
                setAudioAttributes(audioAttributes)
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
                // Fall back to system notification sound
                getDefaultNotificationUri()?.let { uri ->
                    MediaPlayer.create(context, uri)
                }
            }

            finalBellPlayer?.apply {
                setAudioAttributes(audioAttributes)
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

    fun release() {
        intervalBellPlayer?.release()
        intervalBellPlayer = null
        finalBellPlayer?.release()
        finalBellPlayer = null
    }
}
