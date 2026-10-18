package app.czyeru.extension.tiktok.settings.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.view.View;

import app.czyeru.extension.shared.settings.StringSetting;
import app.czyeru.extension.tiktok.playlists.CollectionListPatch;

@SuppressWarnings("deprecation")
public final class CollectionSortPreference extends Preference {
    private final StringSetting setting;

    public CollectionSortPreference(Context context, StringSetting setting) {
        super(context);
        this.setting = setting;
        setPersistent(false);
        setTitle("Sort collections");
        refreshSummary();
    }

    @Override
    protected void onClick() {
        CollectionListPatch.Sort[] sorts = CollectionListPatch.Sort.values();
        String[] labels = new String[sorts.length];
        CollectionListPatch.Sort current = CollectionListPatch.Sort.fromSetting(setting.get());
        int checkedItem = 0;

        for (int index = 0; index < sorts.length; index++) {
            labels[index] = sorts[index].label + " · " + sorts[index].description;
            if (sorts[index] == current) {
                checkedItem = index;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Sort collections")
                .setSingleChoiceItems(labels, checkedItem, (dialogInterface, which) -> {
                    if (which >= 0 && which < sorts.length) {
                        setting.save(sorts[which].key);
                        refreshSummary();
                    }
                    dialogInterface.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        SettingsUi.styleStandardAlertDialog(dialog);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        app.czyeru.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }

    private void refreshSummary() {
        CollectionListPatch.Sort sort = CollectionListPatch.Sort.fromSetting(setting.get());
        setSummary(sort.label + " · " + sort.description
                + ". Applies to the collections page and the save sheet the next time they load.");
    }
}
