package app.czyeru.patches.uvm

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.czyeru.patches.shared.Constants.COMPATIBILITY_UVM
import java.util.logging.Logger

/** The compiled JavaScript of the app. */
private const val BUNDLE = "assets/index.android.bundle"

@Suppress("unused")
val keepSessionPatch = resourcePatch(
    name = "Keep session open",
    description = "Asks the school's login server for an offline session, so the app does not sign you out " +
        "and send you back to the Microsoft login after about 30 minutes. The server must allow offline sessions.",
    default = true
) {
    compatibleWith(COMPATIBILITY_UVM)

    execute {
        val bundle = get(BUNDLE, true)
        if (!bundle.isFile) throw PatchException("$BUNDLE was not found")
        val bytes = bundle.readBytes()
        val report = try {
            UvmSessionPatcher.apply(bytes)
        } catch (e: BundleException) {
            throw PatchException("${e.message}. This patch only supports Conexión Lince UVM 1.0.63.")
        }
        bundle.writeBytes(bytes)

        Logger.getLogger("UvmKeepSession").info("Session kept open: $report")
    }
}
