/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/cleardisplay/Fingerprints.kt
 */
package app.czyeru.patches.tiktok.interaction.cleardisplay

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object OnClearDisplayEventFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/ClearModePanelComponent;") && method.name == "onClearModeEvent"
    },
)

/**
 * The extracted body of PlayerController.onRenderFirstFrame: the only static `(PlayerController,
 * event)V` on the class (`LLIZLLLIL` on 47.2.41, `LLILZIL` on 47.4.3).
 */
internal object OnRenderFirstFrameBodyFingerprint : Fingerprint(
    definingClass = "/feed/controller/PlayerController;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf(
        "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;",
        "L",
    ),
)

internal object ClearModeLogCoreFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Z",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
        "Ljava/lang/String;",
        "J",
        "I",
    ),
)

internal object ClearModeLogStateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Lcom/bytedance/common/utility/collection/WeakHandler;",
        "Z",
        "Ljava/lang/String;",
        "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
        "J",
        "I",
        "I",
    ),
)

internal object ClearModeLogPlaytimeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf(
        "F",
        "I",
        "J",
        "J",
        "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "Z",
    ),
)

