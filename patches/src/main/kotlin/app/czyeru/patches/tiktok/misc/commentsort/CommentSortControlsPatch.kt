package app.czyeru.patches.tiktok.misc.commentsort

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.czyeru.util.getReference
import app.czyeru.util.indexOfFirstInstructionOrThrow
import app.czyeru.util.indexOfFirstStringInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/commentsort/CommentSortControls;"

@Suppress("unused")
val commentSortControlsPatch = bytecodePatch(
    name = "Comment sort controls",
    description = "Exposes TikTok's native full comment-sort sheet, including its hot, time, media, " +
        "and creator modes, instead of relying on rollout gates.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/czyeru/extension/tiktok/settings/SettingsStatus;->enableCommentSortControls()V",
        )

        CommentSortOptionStyleFingerprint.method.apply {
            val styleSettingStringIndex = indexOfFirstStringInstructionOrThrow("comment_sort_opt_style")
            val styleResultIndex = indexOfFirstInstructionOrThrow(
                styleSettingStringIndex + 1,
                Opcode.MOVE_RESULT,
            )
            val styleRegister = getInstruction<OneRegisterInstruction>(styleResultIndex).registerA

            addInstructions(
                styleResultIndex + 1,
                """
                    invoke-static {v$styleRegister}, $EXTENSION_CLASS_DESCRIPTOR->forceOptionStyle(I)I
                    move-result v$styleRegister
                """,
            )
        }

        val eligibilityClass = CommentSortOptionStyleFingerprint.method.implementation!!.instructions
            .first { it.opcode == Opcode.SGET_OBJECT }
            .getReference<FieldReference>()!!.definingClass
        mutableClassDefBy(eligibilityClass).methods.single(::isCommentSortEligibilityMethod).apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->shouldForceSortEligibility()Z
                    move-result v0
                    if-eqz v0, :morphe_stock_comment_sort_eligibility
                    return v0
                """,
                ExternalLabel("morphe_stock_comment_sort_eligibility", getInstruction(0)),
            )
        }
    }
}
