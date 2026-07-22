package com.voxrt.sdk.kws

/**
 * 1:1 JNI facade for `libvoxrt_kws.so` — every method here is bound at
 * `JNI_OnLoad` time via `RegisterNatives` in
 * `crates/voxrt-sdk/voxrt-kws/src/lib.rs::jni_android`. The `.so` never
 * exposes plaintext `Java_com_voxrt_sdk_kws_*` symbols (see ADR-0016
 * Tier B), so this class name + every method name below has an
 * obfstr'd counterpart on the Rust side.
 *
 * Low-level binding — consumers should reach for [VoxrtKwsEngine]
 * (RAII, typed detections, class-name cache).
 */
object VoxrtKwsNative {
    init {
        System.loadLibrary("voxrt_kws")
    }

    external fun voxrtKwsVersion(): String?

    /** Build a session from `.vxrt v2` bytes. Returns the opaque
     *  handle or 0 on error. */
    external fun create(modelBytes: ByteArray): Long

    /** Release a session. Idempotent on 0. */
    external fun destroy(handle: Long)

    /** Wipe streaming state (per-block K/V, DW-conv FIFO, per-class
     *  counters). Post-reset behaviour matches a fresh session. */
    external fun reset(handle: Long): Int

    /** Sigmoid-space threshold in `[0, 1]`, shared across all classes. */
    external fun setThreshold(handle: Long, threshold: Float): Int

    /** Consecutive emits a class posterior must stay ≥ threshold before
     *  firing. Default 3 = 120 ms at 25 fps. */
    external fun setConsecutiveFramesRequired(handle: Long, consecutive: Int): Int

    /** Per-class post-detection cooldown in emits. Default 25 = 1 s at 25 fps. */
    external fun setCooldownFrames(handle: Long, cooldownFrames: Int): Int

    /** Sample rate the session expects on `pushPcm*` (Hz). */
    external fun sampleRate(handle: Long): Int

    /** Number of keyword classes exposed by the loaded model. */
    external fun classCount(handle: Long): Int

    /** UTF-8 class name for `classIndex`, or null on out-of-range /
     *  invalid handle. */
    external fun className(handle: Long, classIndex: Int): String?

    /** Latest per-class sigmoid posteriors as `FloatArray` of length
     *  [classCount]. Zero-filled before the first emit. */
    external fun currentPosteriors(handle: Long): FloatArray?

    /**
     * Push i16 PCM. Returns a 4-tuple-packed `FloatArray` of detections:
     *   `[classIndex_f32, timestampSec, score, emitIndex_f32, …]`
     * Empty array if no detection; null on error.
     */
    external fun pushPcmI16(handle: Long, pcm: ShortArray): FloatArray?

    /** Same as [pushPcmI16] for f32 input in `[-1, 1]`. */
    external fun pushPcmF32(handle: Long, pcm: FloatArray): FloatArray?

    /**
     * Pin the *calling* thread to a CPU cluster. Same semantics as
     * `VoxrtWakeWordNative.setCurrentThreadAffinity` — see the
     * wake-word module for the mode table. Native side discovers
     * cluster boundaries at runtime via sysfs.
     */
    external fun setCurrentThreadAffinity(mode: Int): Boolean
}
