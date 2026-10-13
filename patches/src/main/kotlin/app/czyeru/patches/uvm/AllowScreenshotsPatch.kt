package app.czyeru.patches.uvm

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.czyeru.patches.shared.Constants.COMPATIBILITY_UVM
import app.czyeru.util.findInstructionIndicesReversedOrThrow
import app.czyeru.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val WINDOW = "Landroid/view/Window;"

@Suppress("unused")
val allowScreenshotsPatch = bytecodePatch(
    name = "Allow screenshots",
    description = "Lets you take screenshots and screen recordings in the app, for example of your student ID.",
    default = true
) {
    compatibleWith(COMPATIBILITY_UVM)

    execute {
        PreventScreenshotFingerprint.method.apply {
            val calls = findInstructionIndicesReversedOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL && getReference<MethodReference>()?.let {
                    it.definingClass == WINDOW && it.name == "addFlags" && it.parameterTypes == listOf("I")
                } == true
            }
            if (calls.size != 1) {
                throw PatchException("Expected one Window.addFlags call in the screenshot lock, found ${calls.size}")
            }

            // addFlags(int) and clearFlags(int) have the same signature, so only the name changes.
            // The flag is FLAG_SECURE (0x2000) and the registers stay as they are.
            val index = calls.single()
            val call = getInstruction<FiveRegisterInstruction>(index)
            replaceInstruction(
                index,
                "invoke-virtual {v${call.registerC}, v${call.registerD}}, $WINDOW->clearFlags(I)V"
            )
        }
    }
}
