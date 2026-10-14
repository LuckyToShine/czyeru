package app.czyeru.patches.uvm

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.czyeru.patches.shared.Constants.COMPATIBILITY_UVM
import java.util.logging.Logger

/** The compiled JavaScript of the app. */
private const val BUNDLE = "assets/index.android.bundle"

@Suppress("unused")
val hideSurveyPopupsPatch = resourcePatch(
    name = "Hide survey pop-ups",
    description = "Stops the pop-up with an image, a text and a button that the app shows on the home screen " +
        "for surveys and notices, including forms you already answered.",
    default = true
) {
    compatibleWith(COMPATIBILITY_UVM)

    execute {
        val bundle = get(BUNDLE, true)
        if (!bundle.isFile) throw PatchException("$BUNDLE was not found")
        val bytes = bundle.readBytes()
        val report = try {
            UvmMessagePopupPatcher.apply(bytes)
        } catch (e: BundleException) {
            throw PatchException("${e.message}. This patch only supports Conexión Lince UVM 1.0.63.")
        }
        bundle.writeBytes(bytes)

        Logger.getLogger("UvmSurveyPopups").info("Survey pop-ups hidden: $report")
    }
}
