package app.czyeru.extension.tiktok.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.view.View;

import app.czyeru.extension.shared.Utils;
import app.czyeru.extension.tiktok.playlists.CollectionListPatch;

@SuppressWarnings("deprecation")
public final class ClearCollectionsCachePreference extends Preference {
    public ClearCollectionsCachePreference(Context context) {
        super(context);
        setPersistent(false);
        setTitle("Clear remembered collections");
        setSummary("Forget the remembered collections. Use this if a deleted collection still shows.");
    }

    @Override
    protected void onClick() {
        CollectionListPatch.clearCache();
        Utils.showToastShort("Remembered collections cleared");
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        app.czyeru.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }
}
