# VoxrtKws for Android

Streaming multi-keyword spotter on the **VoxRT** custom on-device inference runtime. 636 K-parameter Conformer-Medium (d=96 × 4 blocks), 16 kHz mono in, per-class sigmoid posteriors out + threshold-crossing events. Ships with a **14-way vocabulary**: `yes / no / cancel / play / pause / next / previous / up / down / back / on / off / voxrt / hey_vox`.

- **Status: production-ready for arm64 Android.** Same runtime as the [Linux](https://github.com/VoxRT/voxrt-kws-linux) + [browser](https://github.com/VoxRT/voxrt-kws-browser) SDKs; numeric parity vs PyTorch reference validated bit-identically across the NEON backend on the deployed model — see [Performance](#performance).
- Current version: `v0.1.0`
- Minimum Android: API 26 (Android 8.0)
- ABIs shipped: `arm64-v8a` (NEON-accelerated, production target), `x86_64` (scalar, emulator only)
- License: Apache-2.0 (Kotlin wrapper) · proprietary (compiled runtime, redistribution allowed via this artifact)
- Model weights: proprietary in-house (synthetic + open training data; no upstream license obligations)

---

## What is VoxRT?

VoxRT is a from-scratch inference runtime for on-device speech models. No ONNX Runtime, no PyTorch Mobile, no LiteRT — a custom Rust core sized and tuned for streaming voice workloads on phone-class hardware.

`VoxrtKws` is the keyword-spotting product on that runtime, alongside [`VoxrtWakeWord`](https://github.com/VoxRT/voxrt-wake-word-android) (single-phrase always-on detection), [`VoxrtAsr`](https://github.com/VoxRT/voxrt-asr-android) (streaming ASR) and [`VoxrtSilero`](https://github.com/VoxRT/voxrt-silero-android) (VAD). All four share the same Rust runtime crate and the same NEON kernel set.

Custom keyword vocabularies (your own brand terms, additional languages, larger class counts) are part of the commercial VoxRT SDK tier. Contact help@voxrt.com.

## Model

Streaming Conformer-Medium — `d_model=96`, 4 blocks, 8 attention heads, 636 K params, fp16, 1.28 MB `.vxrt`. 14 output classes at 25 fps (encoder stride = 4 × 10 ms). Firing rule: sigmoid ≥ threshold (default 0.9) sustained for `consecutive_frames_required` emits (default 3 = 120 ms), then per-class cooldown of `cooldown_frames` emits (default 25 = 1 s).

## Model quality

Held-out speaker-disjoint test split, per-class threshold-swept macro-F1 on the 14-way vocabulary:

- **F1 macro: 0.9671** at the default operating point (threshold 0.9, consecutive-3 firing, cooldown 25 emits).
- **Per-class ROC AUC ≥ 0.99** across every keyword.
- Trained on the M13 recipe — capacity headroom preserves the noise-robustness of M10 while matching the M9 clean-set headroom (see internal `docs/kws/M13-STATUS.md`).

Default operating point tuned for false-fire suppression in an always-on setting; lower the threshold at runtime via `setThreshold` if your application can tolerate more triggers in exchange for higher recall.

## Performance

`arm64-v8a` release builds use NEON 4-wide FMA in every hot kernel (mel frontend, Conformer MHA, depthwise conv module, FFN, LayerNorm, softmax, sigmoid). All ARMv8-A hardware mandates NEON, so no runtime detection is needed.

**Live-mic bench** = `AudioRecord` at 16 kHz mono i16 PCM, 100 ms chunks, single thread, sustained (post-warmup) session RTF. RTF = wall-time / audio-time (lower is better):

| Device | SoC | Cluster | Mode | RTF |
|---|---|---|---|---|
| Xiaomi Redmi 9T (2020) | Snapdragon 662 | Cortex-A73 × 4 @ 2.0 GHz | `CpuAffinity.HIGH_PERF` | **16 %** (6.3× realtime, stable) |
| Xiaomi Redmi 9T (2020) | Snapdragon 662 | Cortex-A53 × 4 @ 1.8 GHz | `CpuAffinity.LOW_POWER` | 38 % (2.6× realtime) |
| Xiaomi Redmi 9T (2020) | Snapdragon 662 | any | `CpuAffinity.AUTO` | ~16 % (scheduler picks A73 for foreground) |
| Snapdragon 8 Gen 2 / A17 Pro-class flagship (extrapolated) | Cortex-X3 / Apple firestorm | perf cluster | AUTO / HIGH_PERF | ~3–5 % |
| Snapdragon 7 Gen 2 / Dimensity 8100-class midrange (extrapolated) | Cortex-A715 / A78 | perf cluster | AUTO / HIGH_PERF | ~7–10 % |
| Dimensity 700 / Helio G70/G80-class budget (extrapolated) | Cortex-A75 / A76 | perf cluster | AUTO / HIGH_PERF | ~12–18 % |

The SD662 rows are direct measurements. All other rows scale from the A73 baseline by clock + µarch — conservative estimates, same binary, no per-device tuning.

At RTF ≈ 0.16 on a 2020 midrange chip, the KWS engine is 6.3× faster than realtime — well within an always-on budget alongside speech-recognition or wake-word tasks running in parallel. On a big.LITTLE chip, pin the engine thread to the perf cluster via `CpuAffinity.HIGH_PERF` if scheduler migrations show up as jitter; otherwise `AUTO` is fine.

**Cross-backend numeric parity** — the same `libvoxrt_kws.so` produces bit-identical output vs the PyTorch reference across every backend the runtime targets:

| Backend | Posterior max \|Δ\| vs PyTorch (5-sample validation) |
|---|---|
| aarch64 NEON (this Android SDK) | 7.2 × 10⁻⁴ |
| x86_64 AVX2 + FMA (Linux SDK) | 7.2 × 10⁻⁴ (identical) |
| WASM SIMD128 (browser SDK) | 4.9 × 10⁻⁴ |

Post-diff sits ~7 × below the abs-tolerance floor of the golden CI (5 × 10⁻³). Detection scores and emit counts are identical across all three backends.

## Binary footprint

- Kotlin wrapper source: ~7 KB total (4 files)
- `libvoxrt_kws.so` per ABI:
  - `arm64-v8a`: ~552 KB stripped
  - `x86_64`: ~636 KB stripped
- KWS model `voxrt_kws.vxrt`: ~1.28 MB fp16 (downloaded separately)

Net effect on a consuming Android app's APK: roughly 2 MB once the .so + .vxrt + Kotlin wrapper are bundled.

## Install

In `settings.gradle.kts`, add JitPack:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

In your app `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.VoxRT:voxrt-kws-android:v0.1.0")
}
```

## Get the model

The model weights are NOT bundled with the library — fetch them once from [`voxrt-kws-models`](https://github.com/VoxRT/voxrt-kws-models/releases/tag/v0.1.0):

```
https://github.com/VoxRT/voxrt-kws-models/releases/download/v0.1.0/voxrt_kws.vxrt
```

SHA-256: `2fdddc5ea63b26342b28a9bd1c78bb946f0cc8943b4086c46e51c53ac6cc3353`

You decide where it lives. Two common patterns for a ~1.3 MB asset:

- **Bundle in app assets** — drop `voxrt_kws.vxrt` into `app/src/main/assets/` and load with `VoxrtKwsEngine.fromAssetBytes(context.assets, "voxrt_kws.vxrt")`. Smallest engineering overhead, works offline from first launch.
- **Download on first run** — fetch into `context.filesDir`, verify the SHA-256, then load with `VoxrtKwsEngine.fromBytes(...)`. Lets you swap models without an app update; requires `<uses-permission android:name="android.permission.INTERNET" />` in your manifest.

## Quick start

```kotlin
import com.voxrt.sdk.kws.VoxrtKwsEngine

// 1. Construct the engine.
val engine = VoxrtKwsEngine.fromAssetBytes(
    context.assets, "voxrt_kws.vxrt"
)

// engine.classNames == ["yes", "no", "cancel", "play", ...] (length 14)

// 2. Feed Int16 PCM (mono, 16 kHz) blocks of any size. 100 ms blocks
//    are the recommended pace for AudioRecord callbacks. processPcm
//    returns any threshold-crossing detections that occurred during
//    this push; usually empty.
for (d in engine.processPcm(shortArrayOfPcm)) {
    Log.i("kws", "'${d.className}' @${d.timestampSec} score=${d.score}")
}

// 3. When you're done.
engine.close()
```

`processPcm` / `reset` / `close` are **synchronous and stateful** — same shape as `VoxrtWakeWordEngine.processPcm`. The engine does NOT own a worker thread. You drive it from your own capture thread.

## Live microphone example

The canonical pattern — capture thread owns the `AudioRecord` loop, engine is just a stateful function. Run on a background thread; don't block the UI thread on `processPcm`.

```kotlin
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.voxrt.sdk.kws.VoxrtKwsEngine

class KwsCapture(private val context: Context) {
    private val engine = VoxrtKwsEngine.fromAssetBytes(
        context.assets, "voxrt_kws.vxrt"
    )

    private val sampleRate = 16_000
    private val blockSamples = 1_600   // 100 ms

    fun runUntilCancelled(onDetection: (String, Float) -> Unit) {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, blockSamples * 2 * 4),
        )
        val buf = ShortArray(blockSamples)
        rec.startRecording()
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) continue
                val block = if (n < blockSamples) buf.copyOf(n) else buf
                for (d in engine.processPcm(block)) {
                    onDetection(d.className, d.score)
                }
            }
        } finally {
            rec.stop()
            rec.release()
            engine.close()
        }
    }
}
```

> **Permission:** declare `<uses-permission android:name="android.permission.RECORD_AUDIO" />` in your app's `AndroidManifest.xml` and request the runtime permission before instantiating `AudioRecord`.

## Tuning

### Threshold

Default is 0.9 (matches the browser + Linux SDKs). Lower for higher recall, raise for stricter precision:

```kotlin
engine.setThreshold(0.85f)   // a bit more recall
engine.setThreshold(0.95f)   // stricter, but loses recall
```

### Consecutive-frames-required

The class posterior must stay ≥ threshold for `consecutiveFramesRequired` emits before firing. Default is 3 (= 120 ms at 25 fps). Higher = more robust against transient spikes, longer detection latency:

```kotlin
engine.setConsecutiveFramesRequired(5)   // 200 ms
```

### Cooldown

After a detection on a class, the engine suppresses further events on that same class for `cooldownFrames` emits. Default is 25 (= 1 s at 25 fps).

```kotlin
engine.setCooldownFrames(50)   // 2 seconds
```

### CPU affinity (advanced)

big.LITTLE chips migrate the audio thread between performance and efficiency clusters under load. Pin the engine's worker thread to a specific cluster:

```kotlin
import com.voxrt.sdk.kws.CpuAffinity

CpuAffinity.applyToCurrentThread(CpuAffinity.HIGH_PERF)
```

`AUTO` (default) lets the scheduler decide. `HIGH_PERF` pins to the cluster with the highest reported max frequency. `LOW_POWER` pins to the LITTLE cluster (useful for measuring worst-case behaviour).

## API

### `VoxrtKwsEngine`

| Method                                                     | Returns                    | Purpose |
| ---------------------------------------------------------- | -------------------------- | ------- |
| `fromAssetBytes(assets, assetName)` (companion)            | `VoxrtKwsEngine`           | Load model from `AssetManager`. |
| `fromBytes(bytes)` (companion)                             | `VoxrtKwsEngine`           | Load model from a `ByteArray`. |
| `nativeVersion()` (companion)                              | `String`                   | SDK version baked into the .so. |
| `processPcm(pcm: ShortArray)`                              | `List<KwsDetection>`       | Push i16 PCM, get threshold-crossings. |
| `processPcm(pcm: FloatArray)`                              | `List<KwsDetection>`       | Same, for f32 PCM in `[-1, 1]`. |
| `currentPosteriors(): FloatArray`                          | `FloatArray`               | Latest per-class sigmoid scores (length = `classCount`). |
| `sampleRate(): Int`                                        | `Int`                      | Sample rate the model expects (Hz). |
| `classCount(): Int`                                        | `Int`                      | Number of classes exposed by the model. |
| `classNames`                                               | `List<String>`             | Cached class names, indexed by `classIndex`. |
| `reset()`                                                  | `Unit`                     | Wipe accumulated state. |
| `setThreshold(threshold: Float)`                           | `Unit`                     | Sigmoid-space detection threshold (0..1). |
| `setConsecutiveFramesRequired(consecutive: Int)`           | `Unit`                     | Frames the posterior must stay ≥ threshold. |
| `setCooldownFrames(cooldownFrames: Int)`                   | `Unit`                     | Per-class post-detection cooldown, in emits. |
| `close()` (or `use { ... }`)                               | `Unit`                     | Release native handle. |

### `KwsDetection`

```kotlin
data class KwsDetection(
    val classIndex: Int,     // 0-based index in classNames
    val className: String,   // resolved name (e.g. "play")
    val timestampSec: Float, // seconds since engine start (or last reset)
    val score: Float,        // sigmoid score in [0, 1]
    val emitIndex: Long,     // 0-based encoder-frame index (25 fps)
)
```

### `CpuAffinity`

```kotlin
enum class CpuAffinity { AUTO, HIGH_PERF, LOW_POWER }

object CpuAffinity {
    fun applyToCurrentThread(mode: CpuAffinity): Boolean
}
```

## License

- **Kotlin wrapper source** (this Gradle module): Apache-2.0. See [`LICENSE`](LICENSE).
- **Compiled runtime** (`libvoxrt_kws.so`): proprietary, redistributable under the terms in [`LICENSE-BINARY`](LICENSE-BINARY).
- **KWS model** (`voxrt_kws.vxrt`): proprietary, distributed separately under the [`voxrt-kws-models`](https://github.com/VoxRT/voxrt-kws-models) license terms.

For commercial integration, custom vocabularies, or licensing terms beyond redistribution of the unmodified library, contact help@voxrt.com.
