/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.czyeru.patches.tiktok.interaction.resume

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.czyeru.patches.shared.compat.AppCompatibilities
import app.czyeru.patches.tiktok.misc.extension.sharedExtensionPatch
import app.czyeru.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.czyeru.util.getReference
import app.czyeru.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_DESCRIPTOR =
    "Lapp/czyeru/extension/tiktok/interaction/ResumeVideoAfterScrollPatch;"

@Suppress("unused")
val resumeVideoAfterScrollPatch = bytecodePatch(
    name = "Resume videos after scrolling",
    description = "Continues supported videos from where playback stopped when returning after a scroll.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/czyeru/extension/tiktok/settings/SettingsStatus;->enableResumeVideoAfterScroll()V",
        )

        FeedProgressContinueGateFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION_DESCRIPTOR->shouldResumeVideoAfterScroll()Z
                    move-result v0
                    if-eqz v0, :continue_gate
                    const/4 v0, 0x1
                    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                    move-result-object v0
                    return-object v0
                """,
                ExternalLabel("continue_gate", getInstruction(0)),
            )
        }

        // The progress holder (`LX/0Mv7;` on 47.2.41, `LX/08V1;` on 47.4.3) keeps its field names
        // across builds but not its own name or its state type, so all of them are read off the
        // progress callback's cache write: the last `LruCache.put(aid, holder.LJ)`.
        val progressMethod = FeedPlayProgressFingerprint.method
        val cachePutIndex = progressMethod.indexOfFirstInstructionReversedOrThrow {
            val reference = getReference<MethodReference>()
            reference?.definingClass == "Landroid/util/LruCache;" &&
                reference.name == "put" &&
                reference.parameterTypes.size == 2
        }
        val cachePut = progressMethod.getInstruction<FiveRegisterInstruction>(cachePutIndex)
        val stateField = progressMethod.getInstruction(
            progressMethod.indexOfFirstInstructionReversedOrThrow(cachePutIndex) {
                opcode == Opcode.SGET_OBJECT && (this as OneRegisterInstruction).registerA == cachePut.registerE
            },
        ).getReference<FieldReference>()!!
        val holder = stateField.definingClass
        val stateType = stateField.type
        val cacheField = progressMethod.getInstruction(
            progressMethod.indexOfFirstInstructionReversedOrThrow(cachePutIndex) {
                opcode == Opcode.SGET_OBJECT && getReference<FieldReference>()?.definingClass == holder &&
                    getReference<FieldReference>() != stateField
            },
        ).getReference<FieldReference>()!!
        // The aid of the remembered state, written by the holder's `(String)State` lookup.
        val stateAidField = mutableClassDefBy(holder).methods.single { method ->
            method.returnType == stateType && method.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;")
        }.implementation!!.instructions.firstNotNullOf { instruction ->
            instruction.takeIf { it.opcode == Opcode.SPUT_OBJECT }?.getReference<FieldReference>()
                ?.takeIf { it.definingClass == holder && it.type == "Ljava/lang/String;" }
        }

        FeedPlayCompletedFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION_DESCRIPTOR->shouldResumeVideoAfterScroll()Z
                    move-result v0
                    if-eqz v0, :continue_completion
                    sget-object v0, $cacheField
                    invoke-interface {v0}, ${cacheField.type}->getValue()Ljava/lang/Object;
                    move-result-object v0
                    check-cast v0, Landroid/util/LruCache;
                    move-object/from16 v1, p1
                    invoke-virtual {v0, v1}, Landroid/util/LruCache;->remove(Ljava/lang/Object;)Ljava/lang/Object;
                    const/4 v0, 0x0
                    sput-object v0, $stateField
                    sput-object v0, $stateAidField
                """,
                ExternalLabel("continue_completion", getInstruction(0)),
            )
        }

        progressMethod.apply {
            val cacheRegister = cachePut.registerC
            val keyRegister = cachePut.registerD
            // The value register is dead after the put, so it doubles as scratch.
            val scratchRegister = cachePut.registerE
            val continueInstruction = getInstruction(cachePutIndex + 1)

            addInstructionsWithLabels(
                cachePutIndex + 1,
                """
                    invoke-static/range {p2 .. p5}, $EXTENSION_DESCRIPTOR->shouldClearCompletedProgress(JJ)Z
                    move-result v$scratchRegister
                    if-eqz v$scratchRegister, :continue_progress
                    invoke-virtual {v$cacheRegister, v$keyRegister}, Landroid/util/LruCache;->remove(Ljava/lang/Object;)Ljava/lang/Object;
                    const/4 v$scratchRegister, 0x0
                    sput-object v$scratchRegister, $stateField
                    sput-object v$scratchRegister, $stateAidField
                """,
                ExternalLabel("continue_progress", continueInstruction),
            )
        }
    }
}
