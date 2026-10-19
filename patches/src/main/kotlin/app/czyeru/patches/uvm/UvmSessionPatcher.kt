package app.czyeru.patches.uvm

/**
 * Asks the school's login server for a long-lived session in Conexión Lince UVM 1.0.63.
 *
 * The app signs in with Keycloak and asks for the scopes `['openid', 'profile']`. The refresh token that
 * comes back dies with the single sign-on session of the server, which is short (the app signs out after
 * about 30 minutes). Adding the scope `offline_access` asks for an offline token, which the server keeps
 * for much longer. The login library joins the scopes with a space, so the second scope becomes
 * `profile offline_access`.
 *
 * The bytecode has no room for a new string, so the text is written over an error message of the login
 * library, `Config error: redirectUrl must be a string`. The app only shows it when the login settings are
 * wrong, and they are not. Then the second element of the scopes array is pointed at that string. Every edit
 * has the same length as what it replaces or is smaller. The patch stops instead of guessing when the code is
 * not the expected one.
 */
internal object UvmSessionPatcher {
    private const val REUSED_STRING = 9428
    private const val REUSED_TEXT = "Config error: redirectUrl must be a string"
    private const val NEW_SCOPES = "profile offline_access"

    /** What was done, for the log. */
    class Report(val scopes: String) {
        override fun toString() = "login scopes are now: openid $scopes"
    }

    /** The bytes of [bundle] are edited in place. */
    fun apply(bundle: ByteArray): Report {
        val before = bundle.copyOf()
        val bytecode = HermesBundle(bundle)
        ensure(bytecode.sourceHash == UvmBundlePatcher.SOURCE_HASH) {
            "This is not the bundle of Conexión Lince UVM 1.0.63 (source ${bytecode.sourceHash})"
        }

        val strings = Array(bytecode.stringCount) { bytecode.string(it) }
        val scopeSites = bytecode.arraySites().filter { site ->
            site.entries.size == 2 && site.entries.all { it.isString } &&
                strings[site.entries[0].value] == "openid" && strings[site.entries[1].value] == "profile"
        }
        ensure(scopeSites.size == 1) { "Expected one scopes array, found ${scopeSites.size}" }
        val profile = scopeSites.single().entries[1]
        ensure(profile.tag == Tag.SHORT_STRING) { "The scopes array is not as expected" }

        ensure(strings[REUSED_STRING] == REUSED_TEXT) { "The string to reuse has changed" }
        val location = bytecode.stringLocation(REUSED_STRING)
        ensure(!location.utf16 && location.length in NEW_SCOPES.length..254) { "The string to reuse is not as expected" }
        ensure(bytecode.stringKinds()[REUSED_STRING] == 0) { "The string to reuse is an identifier" }

        // The text, then its length in the table entry (bits 24..31). The array points at the string.
        val text = NEW_SCOPES.toByteArray(Charsets.ISO_8859_1)
        text.copyInto(bundle, location.offset)
        bytecode.setStringLength(REUSED_STRING, text.size)
        bytecode.putU16(profile.position, REUSED_STRING)

        bytecode.refreshFooter()
        ensure(bytecode.string(REUSED_STRING) == NEW_SCOPES) { "The new text was not written" }

        val allowed = BooleanArray(bundle.size)
        fun allow(from: Int, to: Int) = (from until to).forEach { allowed[it] = true }
        allow(location.offset, location.offset + location.length)
        allow(profile.position, profile.position + 2)
        allow(bytecode.stringEntryOffset(REUSED_STRING), bytecode.stringEntryOffset(REUSED_STRING) + 4)
        allow(bundle.size - HermesBundle.FOOTER_SIZE, bundle.size)
        for (i in bundle.indices) {
            ensure(bundle[i] == before[i] || allowed[i]) { "Byte $i changed without being planned" }
        }
        return Report(NEW_SCOPES)
    }
}
