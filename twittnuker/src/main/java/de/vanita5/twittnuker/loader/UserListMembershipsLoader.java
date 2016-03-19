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

package de.vanita5.twittnuker.loader;

import android.content.Context;

import java.util.List;

import de.vanita5.twittnuker.api.twitter.Twitter;
import de.vanita5.twittnuker.api.twitter.TwitterException;
import de.vanita5.twittnuker.api.twitter.model.PageableResponseList;
import de.vanita5.twittnuker.api.twitter.model.Paging;
import de.vanita5.twittnuker.api.twitter.model.UserList;
import de.vanita5.twittnuker.model.ParcelableUserList;
import de.vanita5.twittnuker.model.UserKey;

public class UserListMembershipsLoader extends BaseUserListsLoader {

    private final String mUserId;
    private final String mScreenName;

    public UserListMembershipsLoader(final Context context, final UserKey accountKey,
                                     final String userId, final String screenName,
                                     final long cursor, final List<ParcelableUserList> data) {
        super(context, accountKey, cursor, data);
        mUserId = userId;
        mScreenName = screenName;
    }

    @Override
    public PageableResponseList<UserList> getUserLists(final Twitter twitter) throws TwitterException {
        if (twitter == null) return null;
        final Paging paging = new Paging();
        paging.cursor(getCursor());
        if (mUserId != null) {
            return twitter.getUserListMemberships(mUserId, paging);
        } else if (mScreenName != null) {
            return twitter.getUserListMembershipsByScreenName(mScreenName, paging);
        }
        return null;
    }

}