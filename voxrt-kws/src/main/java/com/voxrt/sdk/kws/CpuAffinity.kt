package com.voxrt.sdk.kws

/**
 * CPU-cluster pinning for the KWS inference thread. Mirrors the
 * wake-word module's CpuAffinity — same native discovery via sysfs,
 * same soft-fail semantics — but delegates to `VoxrtKwsNative` so a
 * consumer app that only depends on `voxrt-kws-android` doesn't have
 * to also link `voxrt-wake-word-android`.
 *
 * Apply via [applyToCurrentThread] from inside the thread that
 * should be pinned (`pthread_setaffinity_np` only affects the calling
 * tid).
 */
enum class CpuAffinity(val nativeMode: Int) {
    /** Scheduler picks freely. Safe default for always-on / battery use. */
    AUTO(0),

    /** Pin to the highest-frequency cluster (A73 / X-class).
     *  Maximises throughput at higher power cost. */
    HIGH_PERF(1),

    /** Pin to the lowest-frequency cluster (A53 / A55 / A520).
     *  Trades throughput for battery life. */
    LOW_POWER(2);

    companion object {
        fun applyToCurrentThread(mode: CpuAffinity): Boolean {
            return VoxrtKwsNative.setCurrentThreadAffinity(mode.nativeMode)
        }
    }
}
