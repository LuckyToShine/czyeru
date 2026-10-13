package app.czyeru.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_CINEVI = Compatibility(
        name = "Cinevi",
        packageName = "com.movievn.cinevi",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xE50914,
        targets = listOf(
            AppTarget(
                version = "4.0.0"
            )
        )
    )

    val COMPATIBILITY_STICKERLY = Compatibility(
        name = "Sticker.ly",
        packageName = "com.snowcorp.stickerly.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x2E5FFF,
        targets = listOf(
            AppTarget(
                version = "3.37.0"
            )
        )
    )

    val COMPATIBILITY_UVM = Compatibility(
        name = "Conexión Lince UVM",
        packageName = "edu.uvmnet.servicio_alumno",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xD7282F,
        targets = listOf(
            // The patch edits the compiled JavaScript, so it only matches the bundle of this build.
            AppTarget(
                version = "1.0.63"
            )
        )
    )

    val COMPATIBILITY_IRIS_GALLERY = Compatibility(
        name = "Iris Gallery",
        packageName = "com.iris.gallery",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x6750A4,
        targets = listOf(
            // The app is obfuscated, so the fingerprints and the extension only match this build.
            AppTarget(
                version = "0.8.0"
            )
        )
    )

    val COMPATIBILITY_SIMPLE_TIME_TRACKER = Compatibility(
        name = "Simple Time Tracker",
        packageName = "com.razeeman.util.simpletimetracker",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x2196F3,
        targets = listOf(
            // Class and method names are not obfuscated, but the code was only checked in this build.
            AppTarget(
                version = "1.60"
            )
        )
    )
}
