package app.czyeru.extension.tiktok.playlists;

import android.view.View;

import java.lang.reflect.Field;

import app.czyeru.extension.shared.Logger;
import app.czyeru.extension.tiktok.search.ListSearch;
import app.czyeru.extension.tiktok.settings.Settings;

/**
 * Search bar inside a collection (the videos of one collection).
 * <p>
 * Hooked from CollectionContentAssem. Its list items (X.0XiA) hold an Aweme and an int. The
 * header rows at the start of the list are items with an empty Aweme, so they always stay.
 * The adapter clears its list and adds the whole list on every change.
 */
@SuppressWarnings("unused")
public final class CollectionVideosSearchPatch {
    private CollectionVideosSearchPatch() {
    }

    /** Start of CollectionContentAssem.onViewCreated(View), with the list. */
    public static void onListViewCreated(View recyclerView) {
        try {
            if (recyclerView == null || !Settings.COLLECTION_VIDEOS_SEARCH.get()) {
                return;
            }

            // TikTok sets up the list after this. Post so this runs after it.
            recyclerView.post(() -> ListSearch.installWrapped(
                    recyclerView,
                    "Search this collection",
                    CollectionVideosSearchPatch::matches,
                    true
            ));
        } catch (Throwable throwable) {
            Logger.printException(() -> "Collection videos search hook failed", throwable);
        }
    }

    /** Start of the adapter's onBindBasicViewHolder(ViewHolder, int). */
    public static void onBind(Object adapter) {
        ListSearch.onBind(adapter);
    }

    private static boolean matches(Object item, String query) {
        Object aweme = getAweme(item);
        if (aweme == null) {
            return true;
        }
        Object aid = ListSearch.call(aweme, "getAid");
        if (!(aid instanceof String) || ((String) aid).isEmpty()) {
            // Header row.
            return true;
        }
        return ListSearch.awemeMatches(aweme, query);
    }

    private static Object getAweme(Object item) {
        if (item == null) {
            return null;
        }
        for (Field field : item.getClass().getDeclaredFields()) {
            if (field.getType().getName().endsWith(".Aweme")) {
                try {
                    field.setAccessible(true);
                    return field.get(item);
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
