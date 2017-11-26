/*
 *          Twittnuker - Twitter client for Android
 *
 *  Copyright 2013-2017 vanita5 <mail@vanit.as>
 *
 *          This program incorporates a modified version of
 *          Twidere - Twitter client for Android
 *
 *  Copyright 2012-2017 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.vanita5.twittnuker.model.util;

import org.mariotaku.commons.objectcursor.AbsArrayCursorFieldConverter;

import de.vanita5.twittnuker.model.UserKey;

public class FilterUserKeysFieldConverter extends AbsArrayCursorFieldConverter<UserKey> {
    @Override
    protected UserKey[] newArray(int size) {
        return new UserKey[size];
    }

    @Override
    protected String convertToString(UserKey item) {
        if (item == null) return "";
        return '\\' + item.toString() + '\\';
    }

    @Override
    protected UserKey parseItem(String str) {
        if (str == null || str.isEmpty()) return null;
        final int len = str.length();
        if (str.charAt(0) != '\\' || str.charAt(len - 1) != '\\') return UserKey.valueOf(str);
        return UserKey.valueOf(str.substring(1, len - 1));
    }

    @Override
    protected char separatorChar() {
        return '\n';
    }
}