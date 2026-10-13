package app.czyeru.patches.uvm

import app.morphe.patcher.Fingerprint

/**
 * The UI-thread task of `CaptureProtectionModule.prevent()` (react-native-capture-protection).
 * It marks the window as secure with `addFlags(FLAG_SECURE)`, which blocks screenshots, then
 * registers its listeners and resolves the promise of the JavaScript call.
 */
object PreventScreenshotFingerprint : Fingerprint(
    definingClass = "Lcom/captureprotection/CaptureProtectionModule;",
    name = "prevent\$lambda\$10",
    returnType = "V",
    parameters = listOf(
        "Lcom/captureprotection/CaptureProtectionModule;",
        "Lcom/facebook/react/bridge/Promise;"
    )
)
