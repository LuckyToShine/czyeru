/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.czyeru.patches.tiktok.interaction.resume

import app.czyeru.util.getReference
import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * The lazy lambda that turns `FeedPlayProgressContinueConfig.enable` into the continue flag
 * (`LX/0MuL;` on 47.2.41, `LX/08V3;` on 47.4.3).
 */
internal object FeedProgressContinueGateFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    custom = { method, _ ->
        method.name == "invoke" && method.implementation?.instructions?.any { instruction ->
            instruction.getReference<FieldReference>()?.let {
                it.definingClass == "Lcom/ss/android/ugc/aweme/feed/experiment/FeedPlayProgressContinueConfig;" &&
                    it.name == "enable"
            } == true
        } == true
    },
)

internal object FeedPlayCompletedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;" &&
            method.name == "onPlayCompleted"
    },
)

internal object FeedPlayProgressFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "J", "J"),
    custom = { method, classDef ->
        classDef.type == "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;" &&
            method.name == "onPlayProgressChange"
    },
)
