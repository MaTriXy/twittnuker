/*
 * Twittnuker - Twitter client for Android
 *
 * Copyright (C) 2013-2017 vanita5 <mail@vanit.as>
 *
 * This program incorporates a modified version of Twidere.
 * Copyright (C) 2012-2017 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.vanita5.twittnuker.preference;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.util.AttributeSet;

import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.activity.DataExportActivity;
import de.vanita5.twittnuker.activity.DataImportActivity;
import de.vanita5.twittnuker.extension.DialogExtensionsKt;
import de.vanita5.twittnuker.preference.iface.IDialogPreference;

public class SettingsImportExportPreference extends DialogPreference implements IDialogPreference {
    public SettingsImportExportPreference(Context context) {
        this(context, null);
    }

    public SettingsImportExportPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void displayDialog(@NonNull PreferenceFragmentCompat fragment) {
        ImportExportDialogFragment df = ImportExportDialogFragment.newInstance(getKey());
        df.setTargetFragment(fragment, 0);
        df.show(fragment.getFragmentManager(), getKey());
    }

    public static class ImportExportDialogFragment extends PreferenceDialogFragmentCompat {

        public static ImportExportDialogFragment newInstance(String key) {
            final ImportExportDialogFragment df = new ImportExportDialogFragment();
            final Bundle args = new Bundle();
            args.putString(ARG_KEY, key);
            df.setArguments(args);
            return df;
        }


        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            final Context context = getContext();
            final String[] entries = new String[2];
            final Intent[] values = new Intent[2];
            entries[0] = context.getString(R.string.import_settings);
            entries[1] = context.getString(R.string.export_settings);
            values[0] = new Intent(context, DataImportActivity.class);
            values[1] = new Intent(context, DataExportActivity.class);
            builder.setItems(entries, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(values[which]);
                }
            });
            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(final DialogInterface dialog) {
                    DialogExtensionsKt.applyTheme((AlertDialog) dialog);
                }
            });
            return dialog;
        }

        @Override
        public void onDialogClosed(boolean positive) {

        }
    }

}