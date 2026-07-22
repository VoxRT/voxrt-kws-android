package com.voxrt.sdk.kws

/**
 * A single KWS detection event emitted by [VoxrtKwsEngine.processPcm].
 *
 * @property classIndex 0-based class index within the loaded model's
 *     class list. Cross-language stable — prefer this for dispatch.
 * @property className UTF-8 class name (e.g. `"play"`, `"hey_vox"`).
 *     Resolved from the model's manifest, cached engine-side.
 * @property timestampSec Seconds since session start at the firing
 *     encoder frame.
 * @property score Sigmoid posterior in `[0, 1]` at the firing frame.
 * @property emitIndex 0-based encoder-frame index (25 fps at
 *     stem_subsample=4). Useful for cooldown / debouncing logic.
 */
data class KwsDetection(
    val classIndex: Int,
    val className: String,
    val timestampSec: Float,
    val score: Float,
    val emitIndex: Long,
)
