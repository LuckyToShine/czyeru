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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val FEATURE_CONTROLS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/featurecontrols/FeatureControls;"

@Suppress("unused")
val hideQuickCommentReactionsPatch = bytecodePatch(
    name = "Hide quick comment reactions",
    description = "Hides TikTok's exposed quick emoji row in supported comment inputs.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/czyeru/extension/tiktok/settings/SettingsStatus;->enableHideCommentQuickReactions()V",
        )

        QuickCommentReactionGateFingerprint.method.apply {
            implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN }
                .map { it.index }
                .asReversed()
                .forEach { returnIndex ->
                    val returnRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA
                    addInstructions(
                        returnIndex,
                        """
                            invoke-static {v$returnRegister}, $FEATURE_CONTROLS_DESCRIPTOR->keepExposedQuickCommentEmoji(Z)Z
                            move-result v$returnRegister
                        """,
                    )
                }
        }
    }
}
