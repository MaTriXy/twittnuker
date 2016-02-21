/*
 * Twittnuker - Twitter client for Android
 *
 * Copyright (C) 2013-2016 vanita5 <mail@vanit.as>
 *
 * This program incorporates a modified version of Twidere.
 * Copyright (C) 2012-2016 Mariotaku Lee <mariotaku.lee@gmail.com>
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

package de.vanita5.twittnuker.model;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.hannesdorfmann.parcelableplease.annotation.Bagger;
import com.hannesdorfmann.parcelableplease.annotation.ParcelablePlease;
import com.hannesdorfmann.parcelableplease.annotation.ParcelableThisPlease;

import org.json.JSONException;
import org.json.JSONObject;
import org.mariotaku.library.objectcursor.annotation.CursorField;
import org.mariotaku.library.objectcursor.annotation.CursorObject;
import de.vanita5.twittnuker.model.util.BundleConverter;
import de.vanita5.twittnuker.model.util.JSONObjectConverter;
import de.vanita5.twittnuker.model.util.JSONParcelBagger;
import de.vanita5.twittnuker.model.util.LoganSquareCursorFieldConverter;
import de.vanita5.twittnuker.model.util.LongArrayConverter;
import de.vanita5.twittnuker.provider.TwidereDataStore.Drafts;

@ParcelablePlease
@CursorObject(valuesCreator = true)
public class DraftItem implements Parcelable {

    @ParcelableThisPlease
    @CursorField(value = Drafts.ACCOUNT_IDS, converter = LongArrayConverter.class)
    public long[] account_ids;
    @ParcelableThisPlease
    @CursorField(Drafts._ID)
    public long _id;
    @ParcelableThisPlease
    @CursorField(Drafts.TIMESTAMP)
    public long timestamp;
    @ParcelableThisPlease
    @CursorField(Drafts.TEXT)
    public String text;
    @ParcelableThisPlease
    @CursorField(value = Drafts.MEDIA, converter = LoganSquareCursorFieldConverter.class)
    public ParcelableMediaUpdate[] media;
    @ParcelableThisPlease
    @CursorField(value = Drafts.LOCATION, converter = LoganSquareCursorFieldConverter.class)
    public ParcelableLocation location;
    @ParcelableThisPlease
    @CursorField(Drafts.ACTION_TYPE)
    public int action_type;
    @Nullable
    @ParcelableThisPlease
    @CursorField(value = Drafts.ACTION_EXTRAS, converter = BundleConverter.class)
    public Bundle action_extras;


    public DraftItem() {

    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        DraftItemParcelablePlease.writeToParcel(this, dest, flags);
    }


    public static final Creator<DraftItem> CREATOR = new Creator<DraftItem>() {
        public DraftItem createFromParcel(Parcel source) {
            DraftItem target = new DraftItem();
            DraftItemParcelablePlease.readFromParcel(target, source);
            return target;
        }

        public DraftItem[] newArray(int size) {
            return new DraftItem[size];
        }
    };
}