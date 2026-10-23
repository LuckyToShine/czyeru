/*
 * Copyright 2026 LuckyToShine
 * https://github.com/LuckyToShine/czyeru
 */
package app.czyeru.patches.tiktok.shared

import app.czyeru.util.getReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.util.WeakHashMap

/**
 * TikTok wraps most AB values in a two-member holder: a static lazy field filled in `<clinit>` from
 * a lambda that reads the key, and a static getter that unwraps the lazy value. The holder and the
 * getter are renamed on every build (`LX/09fy;->LIZ()I` on 47.2.41 is `LX/09Wl;`'s holder on
 * 47.4.3), while the lambda keeps the key as a string, so the key is what names the getter.
 *
 * <p>The lambda is either its own class (`new-instance L` in the holder's `<clinit>`) or one case
 * of an R8 merged group (`invoke$N`, reached through `L->get$arr$(N)`). Both are followed.
 */
private data class LambdaSite(val type: String, val mergedCase: Long?)

private val holderCache = WeakHashMap<BytecodePatchContext, MutableMap<String, String>>()

private fun lambdaSitesFor(context: BytecodePatchContext, key: String): List<LambdaSite> = with(context) {
    val fingerprint = Fingerprint(
        returnType = "Ljava/lang/Object;",
        strings = listOf(key),
    )
    val sites = mutableListOf<LambdaSite>()
    fingerprint.matchAllOrNull()?.forEach { match ->
        val method = match.originalMethod
        val merged = Regex("""^invoke\$(\d+)$""").find(method.name)?.groupValues?.get(1)?.toLong()
        when {
            method.name == "invoke" && method.parameterTypes.isEmpty() -> sites += LambdaSite(method.definingClass, null)
            merged != null -> sites += LambdaSite(method.definingClass, merged)
        }
    }
    sites
}

private fun ClassDef.initializesLazyFrom(site: LambdaSite): FieldReference? {
    val clinit = methods.firstOrNull { it.name == "<clinit>" } ?: return null
    val instructions = clinit.implementation?.instructions?.toList() ?: return null
    instructions.forEachIndexed { index, instruction ->
        val creates = when (site.mergedCase) {
            null -> instruction.opcode == Opcode.NEW_INSTANCE &&
                instruction.getReference<TypeReference>()?.type == site.type
            else -> instruction.opcode == Opcode.INVOKE_STATIC &&
                instruction.getReference<MethodReference>()?.let {
                    it.definingClass == site.type && it.name == "get\$arr\$"
                } == true &&
                instructions.getOrNull(index - 1)?.let { (it as? WideLiteralInstruction)?.wideLiteral } == site.mergedCase
        }
        if (!creates) return@forEachIndexed
        // The lazy wrapper is stored into this class right after it is built.
        return instructions.drop(index + 1).take(8).firstOrNull { it.opcode == Opcode.SPUT_OBJECT }
            ?.getReference<FieldReference>()
            ?.takeIf { it.definingClass == type }
    }
    return null
}

/**
 * The static getter that unwraps the lazily read AB value [key], as a mutable method.
 *
 * @param returnType the getter's return type, `I` for an int gate and `Z` for a boolean one.
 */
internal fun BytecodePatchContext.lazyAbGetter(key: String, returnType: String): MutableMethod {
    val holderField = synchronized(holderCache) { holderCache.getOrPut(this) { HashMap() }[key] }
        ?.let { cached -> cached.split("|").let { it[0] to it[1] } }
        ?: run {
            val sites = lambdaSitesFor(this, key)
            if (sites.isEmpty()) throw PatchException("No lazy AB lambda reads \"$key\".")
            val found = mutableListOf<FieldReference>()
            classDefForEach { classDef ->
                for (site in sites) classDef.initializesLazyFrom(site)?.let(found::add)
            }
            val field = found.distinctBy { "${it.definingClass}->${it.name}" }.singleOrNull()
                ?: throw PatchException("Expected one lazy holder for \"$key\", found ${found.size}.")
            synchronized(holderCache) {
                holderCache.getOrPut(this) { HashMap() }[key] = "${field.definingClass}|${field.name}"
            }
            field.definingClass to field.name
        }

    val (holderType, fieldName) = holderField
    val getters = mutableClassDefBy(holderType).methods.filter { method ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.returnType == returnType &&
            method.parameterTypes.isEmpty() &&
            method.implementation?.instructions?.any {
                it.opcode == Opcode.SGET_OBJECT && it.getReference<FieldReference>()?.name == fieldName
            } == true
    }
    return getters.singleOrNull()
        ?: throw PatchException("Expected one static ()$returnType getter for \"$key\" on $holderType, found ${getters.size}.")
}
