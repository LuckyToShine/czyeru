package app.czyeru.patches.uvm

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.czyeru.patches.shared.Constants.COMPATIBILITY_UVM
import java.util.logging.Logger
import org.w3c.dom.Element

/** The compiled JavaScript of the app. Every screen is drawn from it. */
private const val BUNDLE = "assets/index.android.bundle"

private const val LIGHT_APP_THEME = "@style/Theme.AppCompat.Light.NoActionBar"
private const val DARK_APP_THEME = "@style/Theme.AppCompat.NoActionBar"

@Suppress("unused")
val uvmAmoledThemePatch = resourcePatch(
    name = "AMOLED dark theme",
    description = "Turns on the dark theme that is inside the app and makes its background black. " +
        "Text and backgrounds that the app sets by itself are changed too. Pictures are not changed.",
    default = true
) {
    compatibleWith(COMPATIBILITY_UVM)

    val amoledColor by stringOption(
        key = "amoledColor",
        default = "#000000",
        values = mapOf(
            "Pure black" to "#000000",
            "Near black" to "#0A0A0A",
            "Dark charcoal" to "#121212",
        ),
        title = "Background color",
        description = "The color of the screens. Pure black turns the pixels off on AMOLED screens.",
        required = true,
        validator = { it != null && Regex("#[0-9A-Fa-f]{6}").matches(it) }
    )

    execute {
        val logger = Logger.getLogger("UvmAmoledTheme")

        val color = amoledColor ?: "#000000"
        val background = color.removePrefix("#").toIntOrNull(16) ?: throw PatchException("Invalid background color $color")

        // The window behind the app, which shows before the first screen and in dialogs, is white by default.
        // The app theme is also the base of the pop-ups of the system, such as date pickers and alerts.
        document("res/values/styles.xml").use { document ->
            val styles = document.getElementsByTagName("style")
            val theme = (0 until styles.length)
                .map { styles.item(it) as Element }
                .firstOrNull { it.getAttribute("name") == "AppTheme" }
                ?: throw PatchException("The AppTheme style was not found")
            if (theme.getAttribute("parent") != LIGHT_APP_THEME) {
                throw PatchException("AppTheme is not based on $LIGHT_APP_THEME. The patch is out of date.")
            }
            theme.setAttribute("parent", DARK_APP_THEME)
            for (name in listOf("android:windowBackground", "android:colorBackground")) {
                val item = document.createElement("item")
                item.setAttribute("name", name)
                item.textContent = color
                theme.appendChild(item)
            }
        }

        // The screens: the bundle has the dark theme of the app but never uses it.
        val bundle = get(BUNDLE, true)
        if (!bundle.isFile) throw PatchException("$BUNDLE was not found")
        val bytes = bundle.readBytes()
        val report = try {
            UvmBundlePatcher.apply(bytes, background)
        } catch (e: BundleException) {
            throw PatchException("${e.message}. This patch only supports Conexión Lince UVM 1.0.63.")
        }
        bundle.writeBytes(bytes)

        logger.info("AMOLED dark theme applied: $report")
    }
}
