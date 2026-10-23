package app.czyeru.patches.tiktok.interaction.autoscroll

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/** `Lfhd/o1;->LIZ()Z` on 47.2.41, `Lgkd/o1;->LIZ()Z` on 47.4.3. */
internal object AutoScrollFeatureGateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("fyp_auto_scroll"),
)

/** `LX/0pm6;->LJIIIIZZ` on 47.2.41, `LX/17ej;->LJIIIIZZ` on 47.4.3. */
internal object AutoScrollActionFactoryFingerprint : Fingerprint(
    parameters = listOf("L"),
    strings = listOf("panel_auto_scroll", "not create auto scroll item, enterFrom:"),
)
