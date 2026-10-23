package app.czyeru.patches.tiktok.misc.likes

import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/likes/LikedVideosSearchPatch;"

private const val SETTINGS_STATUS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/settings/SettingsStatus;"

/** RecyclerView field of the profile video list fragment base class (TikTok 47.2.41). */
private const val LIST_FIELD =
    "Lcom/ss/android/ugc/profile/platform/framework/aweme/profile/tab/profiletab/" +
        "ProfileBaseAwemeListFragment;->LLLFZ:Landroidx/recyclerview/widget/RecyclerView;"

@Suppress("unused")
val likedVideosSearchPatch = bytecodePatch(
    name = "Liked videos search",
    description = "Adds an option to show a search bar on the liked videos page " +
        "that filters loaded videos by description or creator.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, $SETTINGS_STATUS_DESCRIPTOR->enableLikedVideosSearch()V",
        )

        // End of onViewCreated: pass the fragment root and the list to the extension.
        LikesViewCreatedFingerprint.method.apply {
            val implementation = implementation!!
            // iget-object takes 4-bit registers, and p2 is the last register.
            if (implementation.registerCount > 16) {
                throw PatchException(
                    "Liked videos search: onViewCreated uses ${implementation.registerCount} registers.",
                )
            }

            val returnIndices = implementation.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }

            returnIndices.asReversed().forEach { returnIndex ->
                // p2 (the Bundle) is no longer needed once the method is about to return.
                addInstructions(
                    returnIndex,
                    """
                        iget-object p2, p0, $LIST_FIELD
                        invoke-static {p1, p2}, $EXTENSION_CLASS_DESCRIPTOR->onViewCreated(Landroid/view/View;Landroid/view/View;)V
                    """,
                )
            }
        }

        // Give TikTok the unfiltered list before it edits the list by position.
        LikesResumeFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS_DESCRIPTOR->onResume(Ljava/lang/Object;)V",
        )

        // Re-filter when TikTok replaces or extends the list.
        ProfileGridBindViewHolderFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS_DESCRIPTOR->onBind(Ljava/lang/Object;)V",
        )
    }
}
