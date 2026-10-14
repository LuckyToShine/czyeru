package app.czyeru.patches.stickerly

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.czyeru.patches.shared.Constants.COMPATIBILITY_STICKERLY
import app.czyeru.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private const val STICKERLY_PACKAGE_PREFIX = "Lcom/snowcorp/stickerly/"
private const val HELPER_CLASS_DESCRIPTOR = "Lapp/czyeru/extension/stickerly/AmoledHelper;"

val amoledResourcePatch = resourcePatch {
    execute {
        // 1. Patch res/values/colors.xml and res/values-night/colors.xml
        val colorFiles = listOf(
            "res/values/colors.xml",
            "res/values-night/colors.xml"
        )

        for (relativePath in colorFiles) {
            val file = get(relativePath, false)
            if (!file.exists()) continue

            document(relativePath).use { document ->
                val colorNodes = document.getElementsByTagName("color")
                for (i in 0 until colorNodes.length) {
                    val node = colorNodes.item(i) as? Element ?: continue
                    val name = node.getAttribute("name")
                    val currentVal = node.textContent?.trim() ?: continue

                    // Direct name match or intelligent hex conversion
                    val replacement = AmoledColors.namedColorReplacements[name]
                        ?: AmoledConverter.convertHexColor(
                            currentVal,
                            nameOrAttr = name
                        )

                    if (replacement != null) {
                        node.textContent = replacement
                    }
                }
            }
        }

        // 2. Patch styles.xml to ensure windowBackground, statusBarColor, and navigationBarColor are pure black
        val styleFiles = listOf(
            "res/values/styles.xml",
            "res/values-night/styles.xml",
            "res/values-v27/styles.xml",
            "res/values-v29/styles.xml"
        )

        val windowBlackAttrs = setOf(
            "android:windowBackground",
            "windowBackground",
            "android:statusBarColor",
            "statusBarColor",
            "android:navigationBarColor",
            "navigationBarColor",
            "android:colorBackground",
            "colorBackground",
            "colorSurface"
        )

        val lightSystemBarAttrs = setOf(
            "android:windowLightStatusBar",
            "android:windowLightNavigationBar"
        )

        for (relativePath in styleFiles) {
            val file = get(relativePath, false)
            if (!file.exists()) continue

            document(relativePath).use { document ->
                val itemNodes = document.getElementsByTagName("item")
                for (i in 0 until itemNodes.length) {
                    val item = itemNodes.item(i) as? Element ?: continue
                    val attrName = item.getAttribute("name")
                    if (attrName in windowBlackAttrs) {
                        val text = item.textContent?.trim() ?: ""
                        if (text.startsWith("#") || text.contains("white") || text.contains("gray") || text.contains("grey")) {
                            item.textContent = "@android:color/black"
                        }
                    } else if (attrName in lightSystemBarAttrs) {
                        item.textContent = "false"
                    }
                }
            }
        }

        // 3. Fast single-pass scan over res/layout*, res/drawable*, and res/color* XML files
        val resDir = get("res", false)
        if (resDir.exists() && resDir.isDirectory) {
            val targetDirs = resDir.listFiles { dir ->
                dir.isDirectory && (
                    dir.name.startsWith("layout") ||
                    dir.name.startsWith("drawable") ||
                    dir.name.startsWith("color")
                )
            }.orEmpty()

            // Regex to match hardcoded hex color attributes in XML files without full DOM overhead
            val attrHexRegex = Regex("""([a-zA-Z0-9_]+:[a-zA-Z0-9_]+)\s*=\s*"(#[0-9a-fA-F]{3,8})"""")

            for (dir in targetDirs) {
                val xmlFiles = dir.listFiles { f -> f.isFile && f.extension == "xml" }.orEmpty()
                for (xmlFile in xmlFiles) {
                    val content = xmlFile.readText()
                    if (!content.contains('#')) continue

                    var modified = false
                    val updatedContent = attrHexRegex.replace(content) { matchResult ->
                        val attrName = matchResult.groupValues[1]
                        val hexValue = matchResult.groupValues[2]
                        val converted = AmoledConverter.convertHexColor(hexValue, attrName)
                        if (converted != null && !converted.equals(hexValue, ignoreCase = true)) {
                            modified = true
                            """$attrName="$converted""""
                        } else {
                            matchResult.value
                        }
                    }

                    if (modified) {
                        xmlFile.writeText(updatedContent)
                    }
                }
            }
        }
    }
}

@Suppress("unused")
val amoledThemePatch = bytecodePatch(
    name = "AMOLED theme",
    description = "Forces a true black AMOLED theme across all Stickerly screens, Jetpack Compose UI, dialogs, and bottom sheets.",
    default = true
) {
    compatibleWith(COMPATIBILITY_STICKERLY)

    dependsOn(amoledResourcePatch)
    extendWith("extensions/extension.mpe")

    execute {
        // Hook Stickerly Application.onCreate() to initialize global Night Mode & Window Lifecycle callbacks
        classDefForEach { classDef ->
            val type = classDef.type
            if (!type.startsWith(STICKERLY_PACKAGE_PREFIX)) return@classDefForEach

            // Hook Application subclass onCreate()
            if (classDef.superclass == "Landroid/app/Application;" ||
                type.endsWith("StickerlyApplication;") ||
                type.endsWith("App;")
            ) {
                val onCreateMethod = classDef.methods.firstOrNull {
                    it.name == "onCreate" && it.parameterTypes.isEmpty()
                }
                if (onCreateMethod != null) {
                    mutableClassDefBy(type).findMutableMethodOf(onCreateMethod).addInstruction(
                        0,
                        "invoke-static {p0}, $HELPER_CLASS_DESCRIPTOR->init(Landroid/app/Application;)V"
                    )
                }
            }

            // Patch Jetpack Compose 64-bit Color(0xFF......) and 32-bit UI color constants inside Stickerly classes
            val isUiOrComposeClass = type.contains("/ui/") ||
                type.contains("/compose/") ||
                type.contains("/theme/") ||
                type.contains("/design/") ||
                type.contains("/view/") ||
                type.contains("/dialog/") ||
                type.contains("/widget/") ||
                type.contains("/home/") ||
                type.contains("/profile/") ||
                type.contains("/search/") ||
                type.contains("/detail/") ||
                type.contains("/editor/") ||
                type.contains("/settings/")

            for (method in classDef.methods) {
                val impl = method.implementation ?: continue
                val instructions = impl.instructions

                var needsPatch = false
                for (insn in instructions) {
                    when (insn.opcode) {
                        Opcode.CONST_WIDE,
                        Opcode.CONST_WIDE_HIGH16,
                        Opcode.CONST_WIDE_32 -> {
                            val wideLiteral = (insn as? WideLiteralInstruction)?.wideLiteral ?: continue
                            if (AmoledColors.composeWideLiteralReplacements.containsKey(wideLiteral)) {
                                needsPatch = true
                                break
                            }
                        }
                        Opcode.CONST,
                        Opcode.CONST_HIGH16 -> {
                            if (isUiOrComposeClass) {
                                val narrowLiteral = (insn as? NarrowLiteralInstruction)?.narrowLiteral ?: continue
                                if (AmoledColors.narrowLiteralReplacements.containsKey(narrowLiteral)) {
                                    needsPatch = true
                                    break
                                }
                            }
                        }
                        Opcode.INVOKE_VIRTUAL -> {
                            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                            if ((ref.name == "setBackgroundColor" && ref.definingClass == "Landroid/view/View;") ||
                                (ref.name == "setCardBackgroundColor" && ref.definingClass == "Landroidx/cardview/widget/CardView;")
                            ) {
                                needsPatch = true
                                break
                            }
                        }
                        else -> {}
                    }
                }

                if (!needsPatch) continue

                val mutableMethod = mutableClassDefBy(type).findMutableMethodOf(method)
                val mutableInsns = mutableMethod.instructions
                for (index in mutableInsns.size - 1 downTo 0) {
                    val insn = mutableInsns[index]
                    when (insn.opcode) {
                        // Replace 64-bit Jetpack Compose Color(0xFF......) literals
                        Opcode.CONST_WIDE,
                        Opcode.CONST_WIDE_HIGH16,
                        Opcode.CONST_WIDE_32 -> {
                            val wideInsn = insn as? WideLiteralInstruction ?: continue
                            val reg = (insn as? OneRegisterInstruction)?.registerA ?: continue
                            val replacement = AmoledColors.composeWideLiteralReplacements[wideInsn.wideLiteral] ?: continue

                            mutableMethod.replaceInstruction(
                                index,
                                "const-wide v$reg, 0x${java.lang.Long.toHexString(replacement)}L"
                            )
                        }

                        // Replace 32-bit surface/text color literals in UI packages
                        Opcode.CONST,
                        Opcode.CONST_HIGH16 -> {
                            if (!isUiOrComposeClass) continue
                            val narrowInsn = insn as? NarrowLiteralInstruction ?: continue
                            val reg = (insn as? OneRegisterInstruction)?.registerA ?: continue
                            val replacement = AmoledColors.narrowLiteralReplacements[narrowInsn.narrowLiteral] ?: continue

                            if (reg in 0..15) {
                                mutableMethod.replaceInstruction(
                                    index,
                                    "const v$reg, 0x${Integer.toHexString(replacement)}"
                                )
                            }
                        }

                        // Intercept View.setBackgroundColor(int) and CardView.setCardBackgroundColor(int)
                        Opcode.INVOKE_VIRTUAL -> {
                            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                            if (ref.parameterTypes.size == 1 && ref.parameterTypes[0] == "I") {
                                if ((ref.name == "setBackgroundColor" && ref.definingClass == "Landroid/view/View;") ||
                                    (ref.name == "setCardBackgroundColor" && ref.definingClass == "Landroidx/cardview/widget/CardView;")
                                ) {
                                    val twoReg = insn as? TwoRegisterInstruction ?: continue
                                    val colorReg = twoReg.registerB
                                    if (colorReg in 0..15) {
                                        mutableMethod.addInstructions(
                                            index,
                                            """
                                                invoke-static {v$colorReg}, $HELPER_CLASS_DESCRIPTOR->mapBackgroundColor(I)I
                                                move-result v$colorReg
                                            """.trimIndent()
                                        )
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }

        // Hook LayoutInflater.createView / createViewFromTag in androidx AppCompatViewInflater
        classDefByOrNull("Landroidx/appcompat/app/AppCompatViewInflater;")?.let { inflaterClass ->
            val createViewMethod = inflaterClass.methods.firstOrNull {
                it.name == "createView" || it.name == "createViewFromTag"
            }
            if (createViewMethod != null) {
                val mutableMethod = mutableClassDefBy(inflaterClass.type).findMutableMethodOf(createViewMethod)
                val insns = mutableMethod.instructions
                for (i in insns.size - 1 downTo 0) {
                    if (insns[i].opcode == Opcode.RETURN_OBJECT) {
                        val retReg = (insns[i] as OneRegisterInstruction).registerA
                        mutableMethod.addInstruction(
                            i,
                            "invoke-static {v$retReg}, $HELPER_CLASS_DESCRIPTOR->themeSingleView(Landroid/view/View;)V"
                        )
                    }
                }
            }
        }

        // Hook BottomSheetDialog & ComponentDialog onStart() so all popups and sheets get AMOLED windows
        val dialogClasses = setOf(
            "Lcom/google/android/material/bottomsheet/BottomSheetDialog;",
            "Landroidx/appcompat/app/AppCompatDialog;",
            "Landroidx/activity/ComponentDialog;"
        )

        for (dialogClass in dialogClasses) {
            val classDef = classDefByOrNull(dialogClass) ?: continue
            val onStartMethod = classDef.methods.firstOrNull {
                it.name == "onStart" && it.parameterTypes.isEmpty()
            } ?: continue
            val mutableMethod = mutableClassDefBy(classDef.type).findMutableMethodOf(onStartMethod)
            val insns = mutableMethod.instructions
            for (i in insns.size - 1 downTo 0) {
                if (insns[i].opcode == Opcode.RETURN_VOID) {
                    mutableMethod.addInstruction(
                        i,
                        "invoke-static {p0}, $HELPER_CLASS_DESCRIPTOR->onDialogStart(Landroid/app/Dialog;)V"
                    )
                }
            }
        }
    }
}
