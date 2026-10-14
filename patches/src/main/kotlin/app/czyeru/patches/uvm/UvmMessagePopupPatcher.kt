package app.czyeru.patches.uvm

/**
 * Stops the survey pop-up of Conexión Lince UVM 1.0.63.
 *
 * The school sends surveys and notices as "messages". The state of the messages has a flag, `showModal`,
 * and the screen `MessageModal` draws the pop-up with the image, the text and the button while it is on.
 * The app asks for the pop-up again every time the home screen opens, because the school cannot know that
 * a form was answered outside of the app. Three reducers can turn the flag on. Each is changed to leave it
 * off. The inline message cards, which use another flag, are not touched.
 *
 * Every edit has the same length as what it replaces. The patch stops instead of guessing when the code is
 * not the expected one.
 */
internal object UvmMessagePopupPatcher {
    private const val LOAD_CONST_STRING = 115
    private const val LOAD_CONST_TRUE = 120
    private const val LOAD_CONST_FALSE = 121
    private const val LOAD_CONST_UINT8 = 110
    private const val NOT = 11
    private const val OUTPUT_REGISTER = 2

    private const val SHOW_MESSAGE_MODAL = 9267
    private const val SHOW_MESSAGE_MODAL_SIZE = 35
    private const val SHOW_MESSAGE_COMPONENTS = 9269
    private const val SHOW_MESSAGE_COMPONENTS_SIZE = 79
    private const val HIDE_MESSAGE_COMPONENTS = 9270
    private const val HIDE_MESSAGE_COMPONENTS_SIZE = 81

    /** `showModal = true`. */
    private const val TRUE_AT = 0x0B

    /** `showModal = components.includes('Modal')`. The string is replaced, so the answer is always false. */
    private const val MODAL_NAME_AT = 0x16

    /** `showModal = !components.includes('Modal')`. */
    private const val NOT_AT = 0x1F

    /** What was done, for the log. */
    class Report(val edits: Int) {
        override fun toString() = "$edits reducer(s) of the survey pop-up changed"
    }

    /** The bytes of [bundle] are edited in place. */
    fun apply(bundle: ByteArray): Report {
        val before = bundle.copyOf()
        val bytecode = HermesBundle(bundle)
        ensure(bytecode.sourceHash == UvmBundlePatcher.SOURCE_HASH) {
            "This is not the bundle of Conexión Lince UVM 1.0.63 (source ${bytecode.sourceHash})"
        }

        val strings = Array(bytecode.stringCount) { bytecode.string(it) }
        val unrelated = strings.indexOf("payload")
        ensure(unrelated in 0 until 0x10000) { "The string 'payload' was not found" }

        fun start(function: Int, size: Int): Int {
            val (offset, actualSize) = bytecode.function(function)
            ensure(actualSize == size) { "The message reducers have changed (function $function)" }
            return offset
        }

        val allowed = BooleanArray(bundle.size)
        fun allow(from: Int, to: Int) = (from until to).forEach { allowed[it] = true }

        // showMessageModal: LoadConstTrue r2  ->  LoadConstFalse r2
        val modal = start(SHOW_MESSAGE_MODAL, SHOW_MESSAGE_MODAL_SIZE) + TRUE_AT
        ensure(bytecode.readU8(modal) == LOAD_CONST_TRUE && bytecode.readU8(modal + 1) == OUTPUT_REGISTER) {
            "showMessageModal is not as expected"
        }
        bundle[modal] = LOAD_CONST_FALSE.toByte()
        allow(modal, modal + 1)

        // showMessageComponents: includes('Modal')  ->  includes('payload')
        val components = start(SHOW_MESSAGE_COMPONENTS, SHOW_MESSAGE_COMPONENTS_SIZE) + MODAL_NAME_AT
        ensure(bytecode.readU8(components) == LOAD_CONST_STRING && bytecode.readU8(components + 1) == OUTPUT_REGISTER &&
            strings[bytecode.readU16(components + 2)] == "Modal") { "showMessageComponents is not as expected" }
        bytecode.putU16(components + 2, unrelated)
        allow(components + 2, components + 4)

        // hideMessageComponents: Not r2, r2  ->  LoadConstUInt8 r2, 0
        val hide = start(HIDE_MESSAGE_COMPONENTS, HIDE_MESSAGE_COMPONENTS_SIZE) + NOT_AT
        ensure(bytecode.readU8(hide) == NOT && bytecode.readU8(hide + 1) == OUTPUT_REGISTER &&
            bytecode.readU8(hide + 2) == OUTPUT_REGISTER) { "hideMessageComponents is not as expected" }
        bundle[hide] = LOAD_CONST_UINT8.toByte()
        bundle[hide + 1] = OUTPUT_REGISTER.toByte()
        bundle[hide + 2] = 0
        allow(hide, hide + 3)

        allow(bundle.size - HermesBundle.FOOTER_SIZE, bundle.size)
        bytecode.refreshFooter()
        for (i in bundle.indices) {
            ensure(bundle[i] == before[i] || allowed[i]) { "Byte $i changed without being planned" }
        }
        return Report(3)
    }
}
