package app.czyeru.patches.tiktok.interaction.autoscroll

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.czyeru.util.indexOfFirstInstructionOrThrow
import app.czyeru.util.indexOfFirstStringInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/autoscroll/AutoScrollControls;"

@Suppress("unused")
val forceShowAutoScrollPatch = bytecodePatch(
    name = "Force show Auto scroll",
    description = "Adds a setting that bypasses TikTok's rollout gates for its native Auto scroll " +
        "action on supported videos.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/czyeru/extension/tiktok/settings/SettingsStatus;->enableAutoScroll()V",
        )

        AutoScrollFeatureGateFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->shouldForceAutoScroll()Z
                    move-result v0
                    if-eqz v0, :morphe_stock_auto_scroll_feature_gate
                    return v0
                """,
                ExternalLabel("morphe_stock_auto_scroll_feature_gate", getInstruction(0)),
            )
        }

        AutoScrollActionFactoryFingerprint.method.apply {
            val panelGateStringIndex = indexOfFirstStringInstructionOrThrow("panel_auto_scroll")
            val panelGateResultIndex = indexOfFirstInstructionOrThrow(
                panelGateStringIndex + 1,
                Opcode.MOVE_RESULT,
            )
            val panelGateResultRegister =
                getInstruction<OneRegisterInstruction>(panelGateResultIndex).registerA

            addInstructions(
                panelGateResultIndex + 1,
                """
                    invoke-static {v$panelGateResultRegister}, $EXTENSION_CLASS_DESCRIPTOR->forceAutoScrollPanelAvailability(Z)Z
                    move-result v$panelGateResultRegister
                """,
            )
        }
    }
}
