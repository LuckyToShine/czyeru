package app.czyeru.patches.tiktok.interaction.offlinevideos

import app.czyeru.util.getReference
import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object OfflineModeSheetOptionsFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        classDef.endsWith("/OfflineModeSheetPageAssem;") &&
            method.name == "<clinit>" &&
            method.parameterTypes.isEmpty()
    },
)

internal object OfflineModeListConstructorFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        classDef.endsWith("/OfflineModeListVM;") &&
            method.name == "<init>" &&
            method.parameterTypes.isEmpty()
    },
)

/** Static `List` fields a class initializer writes into its own class. */
internal fun ownListFieldWrites(method: com.android.tools.smali.dexlib2.iface.Method, classType: String) =
    method.implementation?.instructions?.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.SPUT_OBJECT) return@mapNotNull null
        instruction.getReference<FieldReference>()?.takeIf { field ->
            field.definingClass == classType && field.type == "Ljava/util/List;"
        }
    } ?: emptyList()

/**
 * The option config holder (`LX/0wj1;` on 47.2.41, `LX/09y8;` on 47.4.3). Its initializer builds
 * four Integer lists, 50..200 and 60..480 plus a reduced variant of each, so the class is told
 * apart from the Keva-backed holder that also lists 150 and 480 by having four of them.
 */
internal object OfflineModeOptionConfigFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    custom = custom@{ method, classDef ->
        if (method.name != "<clinit>") return@custom false
        val literals = method.implementation?.instructions
            ?.mapNotNullTo(HashSet()) { (it as? WideLiteralInstruction)?.wideLiteral }
            ?: return@custom false
        literals.containsAll(listOf(50L, 100L, 150L, 200L, 60L, 120L, 240L, 480L)) &&
            ownListFieldWrites(method, classDef.type).size == 4
    },
)

/** The offline download size enum (`LX/0wiM;` on 47.2.41, `LX/1M2V;` on 47.4.3). */
internal object OfflineModeOptionEnumFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("DOWNLOAD_60_VIDEOS", "DOWNLOAD_200_VIDEOS"),
    custom = { method, _ -> method.name == "<clinit>" },
)
