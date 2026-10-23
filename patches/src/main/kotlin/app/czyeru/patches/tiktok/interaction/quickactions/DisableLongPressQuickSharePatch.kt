/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.czyeru.patches.tiktok.interaction.quickactions

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.czyeru.patches.tiktok.shared.lazyAbGetter
import app.czyeru.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val FEATURE_CONTROLS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/featurecontrols/FeatureControls;"

/** The AB int that turns a long press on Share into quick share. */
private const val LONG_PRESS_QUICK_SHARE_KEY = "im_long_press_share_button_to_quick_share"

@Suppress("unused")
val disableLongPressQuickSharePatch = bytecodePatch(
    name = "Disable long-press quick share",
    description = "Keeps long-pressing Share from opening TikTok's quick-share interaction.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/czyeru/extension/tiktok/settings/SettingsStatus;->enableDisableLongPressQuickShare()V",
        )

        lazyAbGetter(LONG_PRESS_QUICK_SHARE_KEY, "I").apply {
            val returnIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.RETURN
            }
            val register = getInstruction<OneRegisterInstruction>(returnIndex).registerA
            addInstructions(
                returnIndex,
                """
                    invoke-static {v$register}, $FEATURE_CONTROLS_DESCRIPTOR->overrideLongPressQuickShare(I)I
                    move-result v$register
                """,
            )
        }
    }
}
