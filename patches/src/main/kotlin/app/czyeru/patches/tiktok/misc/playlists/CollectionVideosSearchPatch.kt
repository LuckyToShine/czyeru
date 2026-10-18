package app.czyeru.patches.tiktok.misc.playlists

import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/playlists/CollectionVideosSearchPatch;"

private const val SETTINGS_STATUS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/settings/SettingsStatus;"

@Suppress("unused")
val collectionVideosSearchPatch = bytecodePatch(
    name = "Collection videos search",
    description = "Adds an option to show a search bar inside a collection " +
        "that filters loaded videos by description or creator.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok47241())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, $SETTINGS_STATUS_DESCRIPTOR->enableCollectionVideosSearch()V",
        )

        CollectionContentViewCreatedFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p1 .. p1}, $EXTENSION_CLASS_DESCRIPTOR->" +
                "onListViewCreated(Landroid/view/View;)V",
        )

        // Re-filter when TikTok rebuilds the list (load more, refresh, removed video).
        CollectionContentBindViewHolderFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS_DESCRIPTOR->onBind(Ljava/lang/Object;)V",
        )
    }
}
