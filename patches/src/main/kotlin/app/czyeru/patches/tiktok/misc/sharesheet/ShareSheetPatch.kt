package app.czyeru.patches.tiktok.misc.sharesheet

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private const val FILTER_CLASS_DESCRIPTOR = "Lapp/czyeru/extension/tiktok/sharesheet/ShareSheetFilter;"

@Suppress("unused")
val shareSheetPatch = bytecodePatch(
    name = "Share sheet modification",
    description = "Adds toggles and allow-lists for the video share sheet's \"Send to\", " +
        "\"Share via app\", and \"Video Actions\" sections.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/czyeru/extension/tiktok/settings/SettingsStatus;->enableShareSheet()V",
        )

        SharePanelSnapshotConstructorFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, $FILTER_CLASS_DESCRIPTOR->filterChannels(Ljava/lang/Object;)V
                invoke-static {p1}, $FILTER_CLASS_DESCRIPTOR->filterActions(Ljava/lang/Object;)V
                invoke-static {p1}, $FILTER_CLASS_DESCRIPTOR->applySendToVisibility(Ljava/lang/Object;)V
            """,
        )
    }
}
