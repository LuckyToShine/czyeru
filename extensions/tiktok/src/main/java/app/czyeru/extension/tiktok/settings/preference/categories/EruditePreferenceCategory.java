package app.czyeru.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.czyeru.extension.tiktok.settings.Settings;
import app.czyeru.extension.tiktok.settings.SettingsStatus;
import app.czyeru.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public final class EruditePreferenceCategory extends ConditionalPreferenceCategory {
    public EruditePreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Erudite Patches");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.favoritePlaylistGridEnabled
                || SettingsStatus.favoritePlaylistSearchEnabled
                || SettingsStatus.likedVideosSearchEnabled
                || SettingsStatus.chooseCollectionSearchEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.favoritePlaylistGridEnabled || SettingsStatus.favoritePlaylistSearchEnabled) {
            addFavoritePlaylistPreferences(context);
        }
        if (SettingsStatus.likedVideosSearchEnabled) {
            addPreference(group(context, "Liked videos"));
            addPreference(new TogglePreference(
                    context,
                    "Liked videos search bar",
                    "Add a search bar to the liked videos page to filter loaded videos by description "
                            + "or creator. Requires restart.",
                    Settings.LIKED_VIDEOS_SEARCH
            ));
        }
        if (SettingsStatus.chooseCollectionSearchEnabled) {
            addPreference(group(context, "Save to collection"));
            addPreference(new TogglePreference(
                    context,
                    "Collection picker search bar",
                    "Add a search bar to the sheet where you pick a collection for a saved video. "
                            + "Requires restart.",
                    Settings.CHOOSE_COLLECTION_SEARCH
            ));
        }
    }

    private void addFavoritePlaylistPreferences(Context context) {
        addPreference(group(context, "Favorite playlists"));
        if (SettingsStatus.favoritePlaylistGridEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "3-column playlist grid",
                    "Show favorite playlists in a 3-column 8pt grid with square covers, "
                            + "title and video count below. Requires restart.",
                    Settings.FAVORITE_PLAYLIST_GRID
            ));
        }
        if (SettingsStatus.favoritePlaylistSearchEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Playlist search bar",
                    "Add a search bar to the favorite playlists page to filter loaded playlists by name. "
                            + "Requires restart.",
                    Settings.FAVORITE_PLAYLIST_SEARCH
            ));
        }
    }
}
