/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.czyeru.patches.tiktok.misc.absettings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import java.util.WeakHashMap

private const val STRING = "Ljava/lang/String;"

internal const val APP_AB_INT_METHOD = "LJFF"
internal const val APP_AB_INT_KEY_REGISTER = "p3"
internal val APP_AB_INT_PARAMETERS = listOf("I", "I", STRING, "Z")

/**
 * The typed instance getters TikTok's app AB class carries, one per value type, plus the raw one.
 * No other class carries all seven, so together they name the class: `LX/09se;` on 47.2.41 and
 * `LX/02yS;` on 47.4.3. The getter names inside it (`LIZ`, `LJFF`, `LJIIJJI`...) are the same on
 * both builds.
 */
private val APP_AB_SHAPES = setOf(
    "Z" to listOf("I", STRING, "Z", "Z"),
    "D" to listOf("D", "I", STRING, "Z"),
    "F" to listOf("I", STRING, "Z", "F"),
    "I" to APP_AB_INT_PARAMETERS,
    "J" to listOf("I", "J", STRING, "Z"),
    STRING to listOf("I", STRING, STRING, "Z"),
    "Ljava/lang/Object;" to listOf(STRING, "Z"),
)

private fun Method.instanceShape() =
    if (AccessFlags.STATIC.isSet(accessFlags)) null
    else returnType to parameterTypes.map(CharSequence::toString)

private val resolved = WeakHashMap<BytecodePatchContext, String>()

/** The type of the app AB class in the build being patched, found by the getters it carries. */
internal fun BytecodePatchContext.appAbClassType(): String = synchronized(resolved) {
    resolved.getOrPut(this) {
        val carriers = mutableListOf<ClassDef>()
        classDefForEach { classDef ->
            if (classDef.methods.count() < APP_AB_SHAPES.size) return@classDefForEach
            val shapes = classDef.methods.mapNotNullTo(HashSet()) { it.instanceShape() }
            if (shapes.containsAll(APP_AB_SHAPES)) carriers += classDef
        }
        carriers.singleOrNull()?.type ?: throw PatchException(
            "App AB boundary: expected one class carrying all seven AB getter shapes, found ${carriers.size}.",
        )
    }
}

internal fun BytecodePatchContext.appAbClass(): MutableClass = mutableClassDefBy(appAbClassType())

internal fun BytecodePatchContext.hookAppAbIntBoundary(
    extensionDescriptor: String,
    extensionMethod: String,
) {
    appAbClass().methods.single {
        it.name == APP_AB_INT_METHOD && it.instanceShape() == ("I" to APP_AB_INT_PARAMETERS)
    }.apply {
        implementation!!.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN }
            .map { it.index }
            .asReversed()
            .forEach { returnIndex ->
                val register = getInstruction<OneRegisterInstruction>(returnIndex).registerA
                addInstructions(
                    returnIndex,
                    """
                        invoke-static {$APP_AB_INT_KEY_REGISTER, v$register}, $extensionDescriptor->$extensionMethod(Ljava/lang/String;I)I
                        move-result v$register
                    """,
                )
            }
    }
}
