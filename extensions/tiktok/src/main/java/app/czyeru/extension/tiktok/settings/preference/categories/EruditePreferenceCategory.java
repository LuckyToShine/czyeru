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
                || SettingsStatus.favoritePlaylistSearchEnabled;
    }

    @Override
    public void addPreferences(Context context) {
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
