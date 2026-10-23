package app.czyeru.patches.tiktok.misc.playlists

import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/playlists/ChooseCollectionSearchPatch;"

private const val SETTINGS_STATUS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/settings/SettingsStatus;"

@Suppress("unused")
val chooseCollectionSearchPatch = bytecodePatch(
    name = "Collection picker search",
    description = "Adds an option to show a search bar on the sheet where you pick the " +
        "collection for a saved video, so you do not have to scroll through every collection.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, $SETTINGS_STATUS_DESCRIPTOR->enableChooseCollectionSearch()V",
        )

        ChooseCollectionViewCreatedFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p1 .. p1}, $EXTENSION_CLASS_DESCRIPTOR->" +
                "onListViewCreated(Landroid/view/View;)V",
        )

        // Re-filter when TikTok replaces or extends the list (load more, refresh).
        ChooseCollectionBindViewHolderFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS_DESCRIPTOR->onBind(Ljava/lang/Object;)V",
        )
    }
}
