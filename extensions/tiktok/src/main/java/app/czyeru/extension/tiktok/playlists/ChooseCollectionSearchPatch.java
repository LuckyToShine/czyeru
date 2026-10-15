package app.czyeru.extension.tiktok.playlists;

import android.view.View;

import java.lang.reflect.Field;

import app.czyeru.extension.shared.Logger;
import app.czyeru.extension.tiktok.search.ListSearch;
import app.czyeru.extension.tiktok.settings.Settings;

/**
 * "Save to collection" sheet search bar.
 * <p>
 * Hooked from CollectionListChooseAssem. Its list items hold an int view type and a
 * CollectionDetail, the same shape as the favorite playlists page.
 */
@SuppressWarnings("unused")
public final class ChooseCollectionSearchPatch {
    /** Collection item view type in the TikTok adapter. Other types are the "new collection" row. */
    private static final int COLLECTION_VIEW_TYPE = 2;

    private ChooseCollectionSearchPatch() {
    }

    /** Start of CollectionListChooseAssem.onViewCreated(View), with the list. */
    public static void onListViewCreated(View recyclerView) {
        try {
            if (recyclerView == null || !Settings.CHOOSE_COLLECTION_SEARCH.get()) {
                return;
            }

            // TikTok sizes the sheet and sets up the list after this. Post so this runs after it.
            recyclerView.post(() -> ListSearch.installWrapped(
                    recyclerView,
                    "Search collections",
                    ChooseCollectionSearchPatch::matches
            ));
        } catch (Throwable throwable) {
            Logger.printException(() -> "Choose collection search hook failed", throwable);
        }
    }

    /** Start of the adapter's onBindBasicViewHolder(ViewHolder, int). */
    public static void onBind(Object adapter) {
        ListSearch.onBind(adapter);
    }

    private static boolean matches(Object item, String query) {
        Field typeField = null;
        Field detailField = null;
        for (Field field : item.getClass().getDeclaredFields()) {
            if (field.getType() == int.class && typeField == null) {
                typeField = field;
            } else if (field.getType().getName().endsWith(".CollectionDetail")) {
                detailField = field;
            }
        }
        if (typeField == null || detailField == null) {
            return true;
        }

        try {
            typeField.setAccessible(true);
            if (typeField.getInt(item) != COLLECTION_VIEW_TYPE) {
                return true;
            }

            detailField.setAccessible(true);
            Object detail = detailField.get(item);
            Field nameField = detail == null ? null : detail.getClass().getDeclaredField("name");
            if (nameField == null) {
                return true;
            }
            nameField.setAccessible(true);
            Object name = nameField.get(detail);
            return name instanceof String && ListSearch.containsAllWords((String) name, query);
        } catch (Throwable throwable) {
            return true;
        }
    }
}
