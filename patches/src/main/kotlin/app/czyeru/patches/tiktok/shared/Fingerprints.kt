/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/shared/Fingerprints.kt
 */
package app.czyeru.patches.tiktok.shared

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object GetEnterFromFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Z"),
    custom = { method, classDef ->
        method.definingClass.endsWith("/BaseListFragmentPanel;") &&
            method.returnType == "Ljava/lang/String;" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Z"
    },
)

internal object OnRenderFirstFrameFingerprint : Fingerprint(
    definingClass = "/feed/controller/PlayerController;",
    name = "onRenderFirstFrame",
    returnType = "V",
    // The first-frame event, LX/0qPZ; on 47.2.41 and LX/09cW; on 47.4.3.
    parameters = listOf("L"),
)

