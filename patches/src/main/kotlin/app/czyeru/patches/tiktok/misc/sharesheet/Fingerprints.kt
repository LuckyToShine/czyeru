package app.czyeru.patches.tiktok.misc.sharesheet

import app.czyeru.util.getReference
import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * The share panel snapshot built from its builder (`X.0puG(X.0puH)` on 47.2.41, `X.0IG6(X.0I8f)` on
 * 47.4.3). It is the only constructor that takes one object and copies the builder's share
 * package into a field of its own. The builder keeps its field names across both builds, which
 * the extension reads.
 */
internal object SharePanelSnapshotConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("L"),
    custom = custom@{ method, classDef ->
        val builderType = method.parameterTypes.single().toString()
        val instructions = method.implementation?.instructions ?: return@custom false
        instructions.any { instruction ->
            instruction.opcode == Opcode.IGET_OBJECT &&
                instruction.getReference<FieldReference>()?.let {
                    it.definingClass == builderType &&
                        it.type == "Lcom/ss/android/ugc/aweme/share/base/model/BaseSharePackage;"
                } == true
        } && instructions.any { instruction ->
            instruction.opcode == Opcode.IPUT_OBJECT &&
                instruction.getReference<FieldReference>()?.let {
                    it.definingClass == classDef.type &&
                        it.type == "Lcom/ss/android/ugc/aweme/share/base/model/BaseSharePackage;"
                } == true
        }
    },
)
