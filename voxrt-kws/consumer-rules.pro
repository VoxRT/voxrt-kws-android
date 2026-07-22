# ProGuard / R8 rules consumed by apps that depend on voxrt-kws.
#
# KWS v0.1.0 uses RegisterNatives (ADR-0016 Tier B anti-RE) — same
# path as voxrt-wake-word. `VoxrtKwsNative` is a Kotlin `object`, so
# in JVM bytecode it is:
#
#   public final class com.voxrt.sdk.kws.VoxrtKwsNative {
#       public static final com.voxrt.sdk.kws.VoxrtKwsNative INSTANCE;
#       public final native long create(byte[]);
#       public final native float[] currentPosteriors(long);
#       ...etc
#   }
#
# If R8 renames the class or its methods, `env.find_class(...)` /
# `RegisterNatives` inside libvoxrt_kws.so silently miss and every
# native call throws `UnsatisfiedLinkError`. Keep the class name,
# every member, and the INSTANCE field.

-keep class com.voxrt.sdk.kws.VoxrtKwsNative {
    public static ** INSTANCE;
    public static <fields>;
    public <methods>;
    native <methods>;
}

# Defence in depth: any class with native methods keeps them under
# their original names so JNI resolution still works.
-keepclasseswithmembernames class * {
    native <methods>;
}

# `VoxrtKwsEngine` is the public entry point consumers touch. Keep
# its public API surface (fromBytes / fromAssetBytes / processPcm /
# reset / currentPosteriors / setThreshold / setConsecutiveFramesRequired
# / setCooldownFrames / sampleRate / classCount / close / nativeVersion).
-keep class com.voxrt.sdk.kws.VoxrtKwsEngine {
    public *;
}

# CpuAffinity — enum values must survive R8 so clients passing
# `CpuAffinity.AUTO` / `HIGH_PERF` / `LOW_POWER` still match JNI.
-keep class com.voxrt.sdk.kws.CpuAffinity { *; }

# KwsDetection is a data class returned from processPcm. Keep fields
# so consumers can read classIndex / className / timestampSec / score
# / emitIndex off events at runtime.
-keep class com.voxrt.sdk.kws.KwsDetection { *; }
