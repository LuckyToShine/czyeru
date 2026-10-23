package app.czyeru.patches.tiktok.misc.commentsort

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The lazy initializer that reads the `comment_sort_opt_style` AB value. 47.2.41 keeps it in a
 * shared lambda holder (`AFwS250S0000000_20.invoke$182`), 47.4.3 in its own `invoke()` class, so
 * only the string and the boxed return identify it. The static `()V` that also names the key is a
 * settings registrar and returns nothing.
 */
internal object CommentSortOptionStyleFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf("comment_sort_opt_style"),
)

/**
 * The sort eligibility check `static boolean (Aweme)`. It lives on the singleton whose instance
 * [CommentSortOptionStyleFingerprint] reads (`LX/0ls3;` on 47.2.41, `LX/0FQV;` on 47.4.3), so the
 * class is resolved from that read rather than by name.
 */
internal fun isCommentSortEligibilityMethod(
    method: com.android.tools.smali.dexlib2.iface.Method,
) = method.returnType == "Z" &&
    method.parameterTypes.map(CharSequence::toString) == listOf("Lcom/ss/android/ugc/aweme/feed/model/Aweme;") &&
    AccessFlags.STATIC.isSet(method.accessFlags)
