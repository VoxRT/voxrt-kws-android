package com.voxrt.sdk.kws

import android.content.res.AssetManager
import java.io.Closeable
import java.io.IOException

/**
 * Idiomatic Kotlin wrapper around the KWS native library. Mirrors
 * `VoxrtWakeWordEngine` in shape, adapted for the multi-class output:
 * detections carry both `classIndex` and the resolved `className`.
 *
 * Lifecycle:
 *   - Construct via [fromAssetBytes] / [fromBytes].
 *   - Drive via [processPcm]; each call returns zero or more
 *     [KwsDetection] events.
 *   - Read [currentPosteriors] any time for a per-class continuous UI.
 *   - Close via [close] (or `use { … }`) to release the native handle.
 *
 * Threading: not thread-safe. Drive from one capture thread.
 */
class VoxrtKwsEngine private constructor(
    initialHandle: Long,
) : Closeable {

    @Volatile private var handle: Long = initialHandle

    /** Cached class-name array (one lookup per engine, not per detection). */
    val classNames: List<String>

    init {
        require(handle != 0L) { "voxrt_kws_create returned 0 (model load failed)" }
        val n = VoxrtKwsNative.classCount(handle)
        classNames = (0 until n).map { i ->
            VoxrtKwsNative.className(handle, i) ?: "<class_$i>"
        }
    }

    /** Sample rate the model expects (Hz) — read from the .vxrt manifest. */
    fun sampleRate(): Int = if (handle == 0L) 0 else VoxrtKwsNative.sampleRate(handle)

    /** Number of classes exposed by the loaded model. */
    fun classCount(): Int = classNames.size

    /** Sigmoid-space threshold (0..1). Default 0.9. */
    fun setThreshold(threshold: Float) {
        if (handle != 0L) VoxrtKwsNative.setThreshold(handle, threshold)
    }

    /** Consecutive-frames-required (default 3 = 120 ms at 25 fps). */
    fun setConsecutiveFramesRequired(consecutive: Int) {
        if (handle != 0L) VoxrtKwsNative.setConsecutiveFramesRequired(handle, consecutive)
    }

    /** Per-class cooldown after a detection, in emits. Default 25 = 1 s at 25 fps. */
    fun setCooldownFrames(cooldownFrames: Int) {
        if (handle != 0L) VoxrtKwsNative.setCooldownFrames(handle, cooldownFrames)
    }

    /** Latest per-class sigmoid posteriors as `FloatArray[classCount]`. */
    fun currentPosteriors(): FloatArray =
        if (handle == 0L) FloatArray(0) else VoxrtKwsNative.currentPosteriors(handle) ?: FloatArray(0)

    /** Wipe streaming state. */
    fun reset() {
        if (handle != 0L) VoxrtKwsNative.reset(handle)
    }

    /** Push i16 PCM (mono, [sampleRate], native-endian). */
    fun processPcm(pcm: ShortArray): List<KwsDetection> {
        if (handle == 0L) return emptyList()
        val flat = VoxrtKwsNative.pushPcmI16(handle, pcm) ?: return emptyList()
        return decode(flat)
    }

    /** Push f32 PCM (mono, [sampleRate], range `[-1, 1]`). */
    fun processPcm(pcm: FloatArray): List<KwsDetection> {
        if (handle == 0L) return emptyList()
        val flat = VoxrtKwsNative.pushPcmF32(handle, pcm) ?: return emptyList()
        return decode(flat)
    }

    override fun close() {
        val h = handle
        if (h != 0L) {
            handle = 0L
            VoxrtKwsNative.destroy(h)
        }
    }

    private fun decode(flat: FloatArray): List<KwsDetection> {
        if (flat.isEmpty()) return emptyList()
        require(flat.size % 4 == 0) { "malformed detection array, size=${flat.size}" }
        val n = flat.size / 4
        val out = ArrayList<KwsDetection>(n)
        for (i in 0 until n) {
            val base = i * 4
            val idx = flat[base].toInt()
            val name = classNames.getOrElse(idx) { "<class_$idx>" }
            out.add(
                KwsDetection(
                    classIndex = idx,
                    className = name,
                    timestampSec = flat[base + 1],
                    score = flat[base + 2],
                    emitIndex = flat[base + 3].toLong(),
                )
            )
        }
        return out
    }

    companion object {
        /** Load a `.vxrt` model bundled as an APK asset. */
        @Throws(IOException::class)
        fun fromAssetBytes(assets: AssetManager, assetName: String): VoxrtKwsEngine {
            val bytes = assets.open(assetName).use { it.readBytes() }
            val handle = VoxrtKwsNative.create(bytes)
            if (handle == 0L) {
                throw IOException("voxrt_kws_create failed for asset '$assetName'")
            }
            return VoxrtKwsEngine(handle)
        }

        /** Load from raw bytes already in memory. */
        fun fromBytes(bytes: ByteArray): VoxrtKwsEngine {
            val handle = VoxrtKwsNative.create(bytes)
            require(handle != 0L) { "voxrt_kws_create failed" }
            return VoxrtKwsEngine(handle)
        }

        /** NUL-stripped SDK version string. */
        fun nativeVersion(): String = VoxrtKwsNative.voxrtKwsVersion() ?: "unknown"
    }
}
