package com.shiyin.player.data.audio

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor

/**
 * ExoPlayer 的音频处理链：静音跳过 → 变速（保音高）→ 均衡器。
 *
 * 不直接用 `DefaultAudioProcessorChain`：那样要么丢掉自己的 processor，
 * 要么丢掉变速用的 SonicAudioProcessor（丢掉它，1.5 倍速就会变成「快进的老磁带」）。
 * 这里把三者按顺序拼好，变速参数显式转发给 Sonic。
 */
@UnstableApi
class ShiYinAudioChain(
    equalizer: AudioProcessor,
) : AudioProcessorChain {

    private val silence = SilenceSkippingAudioProcessor()
    private val sonic = SonicAudioProcessor()
    private val processors = arrayOf<AudioProcessor>(silence, sonic, equalizer)

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        sonic.setSpeed(playbackParameters.speed)
        sonic.setPitch(playbackParameters.pitch)
        return playbackParameters
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
        silence.setEnabled(skipSilenceEnabled)
        return skipSilenceEnabled
    }

    override fun getMediaDuration(mediaDurationUs: Long): Long = sonic.getMediaDuration(mediaDurationUs)

    override fun getSkippedOutputFrameCount(): Long = silence.skippedFrames
}
