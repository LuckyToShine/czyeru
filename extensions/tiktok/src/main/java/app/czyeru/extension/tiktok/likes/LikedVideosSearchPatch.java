package app.czyeru.extension.tiktok.likes;

import android.view.View;

import app.czyeru.extension.shared.Logger;
import app.czyeru.extension.tiktok.search.ListSearch;
import app.czyeru.extension.tiktok.settings.Settings;

/**
 * Liked videos page (Profile > Liked) search bar.
 * <p>
 * Hooked from ProfileLikesAwemeListFragment. The grid adapter holds Aweme items, and Aweme and
 * User keep their real getter names, so the match reads them by reflection.
 */
@SuppressWarnings("unused")
public final class LikedVideosSearchPatch {
    private LikedVideosSearchPatch() {
    }

    /** End of ProfileLikesAwemeListFragment.onViewCreated, with the fragment root and its list. */
    public static void onViewCreated(View root, View recyclerView) {
        try {
            if (recyclerView == null || !Settings.LIKED_VIDEOS_SEARCH.get()) {
                return;
            }

            // TikTok finishes laying out the tab after onViewCreated. Post so this runs after it.
            recyclerView.post(() -> ListSearch.installOverlay(
                    recyclerView,
                    root,
                    "Search liked videos",
                    LikedVideosSearchPatch::matches
            ));
        } catch (Throwable throwable) {
            Logger.printException(() -> "Liked videos search hook failed", throwable);
        }
    }

    /** Start of the profile grid adapter's onBindBasicViewHolder. */
    public static void onBind(Object adapter) {
        ListSearch.onBind(adapter);
    }

    /** Start of ProfileLikesAwemeListFragment.onResume. */
    public static void onResume(Object fragment) {
        ListSearch.onResume(fragment);
    }

    /** Matches the description, title, creator name and creator username. */
    private static boolean matches(Object item, String query) {
        Object author = ListSearch.call(item, "getAuthor");
        return ListSearch.containsAllWords(text(ListSearch.call(item, "getDesc")), query)
                || ListSearch.containsAllWords(text(ListSearch.call(item, "getTitle")), query)
                || ListSearch.containsAllWords(text(ListSearch.call(author, "getNickname")), query)
                || ListSearch.containsAllWords(text(ListSearch.call(author, "getUniqueId")), query);
    }

    private static String text(Object value) {
        return value instanceof String ? (String) value : null;
    }
}
