package app.czyeru.patches.tiktok.misc.playlists

import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/playlists/CollectionListPatch;"

private const val SETTINGS_STATUS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/settings/SettingsStatus;"

private const val COLLECTION_LIST_RESPONSE =
    "Lcom/ss/android/ugc/aweme/favorites/business/collection/CollectionListResponse;"

/**
 * Passes every loaded collections page to the extension before it becomes the list state.
 * Shared by the keep loaded and sort patches, so it is added once.
 */
private val collectionListHookPatch = bytecodePatch {
    dependsOn(sharedExtensionPatch)

    execute {
        CollectionListPageLoadedFingerprint.method.apply {
            val instructions = implementation!!.instructions.toList()

            // The state reducer is built from (new list, response, Boolean, case).
            val initIndex = instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.INVOKE_DIRECT &&
                    ((instruction as ReferenceInstruction).reference as MethodReference).let {
                        it.name == "<init>" && it.parameterTypes.size == 4 &&
                            it.parameterTypes[0] == "Ljava/util/List;" &&
                            it.parameterTypes[1] == COLLECTION_LIST_RESPONSE
                    }
            }
            if (initIndex < 0) {
                throw PatchException("Collections list: state reducer not found.")
            }
            val init = instructions[initIndex] as FiveRegisterInstruction
            val listRegister = init.registerD
            val responseRegister = init.registerE
            val reducerType = ((init as ReferenceInstruction).reference as MethodReference).definingClass

            // Call before new-instance, where only the reducer's own registers are written next.
            val newInstanceIndex = instructions.subList(0, initIndex).indexOfLast { instruction ->
                instruction.opcode == Opcode.NEW_INSTANCE &&
                    (instruction as ReferenceInstruction).reference.toString() == reducerType
            }
            if (newInstanceIndex < 0) {
                throw PatchException("Collections list: state reducer allocation not found.")
            }

            addInstruction(
                newInstanceIndex,
                "invoke-static { v$listRegister, v$responseRegister, p0 }, $EXTENSION_CLASS_DESCRIPTOR->" +
                    "onCollectionsLoaded(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)V",
            )
        }
    }
}

@Suppress("unused")
val keepCollectionsLoadedPatch = bytecodePatch(
    name = "Keep collections loaded",
    description = "Adds an option to remember every collection you have loaded, so the collections " +
        "page and the save sheet show them all right away instead of loading them page by page again.",
    default = true,
) {
    dependsOn(collectionListHookPatch)

    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, $SETTINGS_STATUS_DESCRIPTOR->enableKeepCollectionsLoaded()V",
        )
    }
}

@Suppress("unused")
val sortCollectionsPatch = bytecodePatch(
    name = "Sort collections",
    description = "Adds an option to sort collections by recent activity (normal), by video count " +
        "(most first) or by reverse video count (fewest first).",
    default = true,
) {
    dependsOn(collectionListHookPatch)

    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, $SETTINGS_STATUS_DESCRIPTOR->enableCollectionSort()V",
        )
    }
}
