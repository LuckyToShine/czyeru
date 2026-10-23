/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/speed/Fingerprints.kt
 */
package app.czyeru.patches.tiktok.interaction.speed

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object PlaybackSpeedSelectionBoundaryFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "F",
        "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    strings = listOf(
        "swipe_up_lock_persist",
        "click_share_button",
        "long_press",
    ),
)

/** `LJJIJL(F)V` on 47.2.41 and `LIZ(F)V` on 47.4.3; the one that logs the speed change. */
internal object PlayerControllerSetSpeedFingerprint : Fingerprint(
    definingClass = "/feed/controller/PlayerController;",
    returnType = "V",
    parameters = listOf("F"),
    strings = listOf("speed_begin"),
)

internal object SetSpeedFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    strings = listOf("playback_speed"),
    custom = { method, _ ->
        method.name == "invoke" && method.parameterTypes.isEmpty()
    },
)

internal object LongPressSpeedUpEnableFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("long_press_speed_up_enable"),
)

internal object LongPressSpeedUpLockFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    strings = listOf("long_press_speed_up_lock"),
)

