/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.czyeru.patches.tiktok.captchapopup

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.czyeru.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val FEATURE_CONTROLS_CLASS_DESCRIPTOR = "Lapp/czyeru/extension/tiktok/featurecontrols/FeatureControls;"

private object CaptchaPopupFingerprint : Fingerprint(
    definingClass = "/sec/SecApiImpl;",
    name = "popCaptchaV2",
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "Ljava/lang/String;",
        "L", // callback, LX/0Vtt; on 47.2.41 and LX/1E1A; on 47.4.3
        "Landroidx/fragment/app/Fragment;",
        "Ljava/lang/String;",
    ),
    strings = listOf("popCaptchaV2 - riskInfo ="),
)

private object LegacyCaptchaPopupFingerprint : Fingerprint(
    definingClass = "/sec/SecApiImpl;",
    name = "popCaptcha",
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;", "I", "L"),
    strings = listOf("popCaptcha - errorcode = "),
)

private object OecCaptchaPopupFingerprint : Fingerprint(
    definingClass = "Lcom/tts/oecverify/verify/RiskControlService;",
    name = "execute",
    returnType = "Z",
    parameters = listOf("L", "Lcom/tts/oecverify/BdTuringCallback;"),
)

private object LiveHostCaptchaPopupFingerprint : Fingerprint(
    definingClass = "/live/livehostimpl/LiveHostUser;",
    name = "popCaptchaV2",
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "Ljava/lang/String;",
        "L",
        "Landroidx/fragment/app/Fragment;",
    ),
)

private object BdTuringCaptchaPopupFingerprint : Fingerprint(
    definingClass = "Lcom/tts/oecverify/BdTuring;",
    name = "showVerifyDialog",
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "L",
        "Lcom/tts/oecverify/BdTuringCallback;",
    ),
)

@Suppress("unused")
val hideCaptchaPopupsPatch = bytecodePatch(
    name = "Hide CAPTCHA popups",
    description = "Adds a default-off setting to hide browsing and LIVE puzzle dialogs while preserving login and account verification.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/czyeru/extension/tiktok/settings/SettingsStatus;->enableCaptchaPopupSuppression()V",
        )

        // The SecApi callback is renamed per build (LX/0Vtt; on 47.2.41, LX/1E1A; on 47.4.3) but keeps
        // its `LIZJ()V` cancel hook, so its type is read off the matched method.
        val secCallbackType = CaptchaPopupFingerprint.method.parameterTypes[2]
        CaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideCaptchaPopup(Landroid/app/Activity;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :morphe_show_captcha_popup
                if-eqz p3, :morphe_hide_captcha_popup_return
                invoke-virtual {p3}, $secCallbackType->LIZJ()V
                :morphe_hide_captcha_popup_return
                return-void
                :morphe_show_captcha_popup
                nop
            """,
        )

        val legacyCallbackType = LegacyCaptchaPopupFingerprint.method.parameterTypes[2]
        LegacyCaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideCaptchaPopup(Landroid/app/Activity;)Z
                move-result v0
                if-eqz v0, :morphe_show_legacy_captcha_popup
                if-eqz p3, :morphe_hide_legacy_captcha_popup_return
                invoke-virtual {p3}, $legacyCallbackType->LIZJ()V
                :morphe_hide_legacy_captcha_popup_return
                return-void
                :morphe_show_legacy_captcha_popup
                nop
            """,
        )

        OecCaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideCaptchaPopup()Z
                move-result v0
                if-eqz v0, :morphe_show_oec_captcha_popup
                const/4 v0, 0x3
                const/4 v1, 0x0
                move-object/from16 v2, p2
                invoke-interface {v2, v0, v1}, Lcom/tts/oecverify/BdTuringCallback;->onFail(ILorg/json/JSONObject;)V
                const/4 v0, 0x1
                return v0
                :morphe_show_oec_captcha_popup
                nop
            """,
        )

        // The LIVE callback interface renames its methods per build (`LIZIZ` on 47.2.41, `LIZJ` on
        // 47.4.3). popCaptchaV2 itself calls the one the patch needs, so it is read from there.
        val liveCallback = LiveHostCaptchaPopupFingerprint.method.let { method ->
            val callbackType = method.parameterTypes[2].toString()
            method.implementation!!.instructions.mapNotNull { instruction ->
                instruction.takeIf { it.opcode == Opcode.INVOKE_INTERFACE }?.getReference<MethodReference>()
            }.firstOrNull { reference ->
                reference.definingClass == callbackType && reference.returnType == "V" && reference.parameterTypes.isEmpty()
            } ?: throw PatchException("LIVE captcha: popCaptchaV2 never calls its $callbackType callback.")
        }
        LiveHostCaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideCaptchaPopup(Landroid/app/Activity;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :morphe_show_live_captcha_popup
                if-eqz p3, :morphe_hide_live_captcha_popup_return
                invoke-interface {p3}, ${liveCallback.definingClass}->${liveCallback.name}()V
                :morphe_hide_live_captcha_popup_return
                return-void
                :morphe_show_live_captcha_popup
                nop
            """,
        )

        // Network verification can present Turing directly without passing through SecApiImpl.
        BdTuringCaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideCaptchaPopup(Landroid/app/Activity;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :morphe_show_turing_captcha_popup
                if-eqz p3, :morphe_hide_turing_captcha_popup_return
                const/4 v0, 0x3
                const/4 v1, 0x0
                invoke-interface {p3, v0, v1}, Lcom/tts/oecverify/BdTuringCallback;->onFail(ILorg/json/JSONObject;)V
                :morphe_hide_turing_captcha_popup_return
                return-void
                :morphe_show_turing_captcha_popup
                nop
            """,
        )
    }
}
