/*
 * Twittnuker - Twitter client for Android
 *
 * Copyright (C) 2013-2015 vanita5 <mail@vanita5.de>
 *
 * This program incorporates a modified version of Twidere.
 * Copyright (C) 2012-2015 Mariotaku Lee <mariotaku.lee@gmail.com>
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

package de.vanita5.twittnuker.fragment.support;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.Loader;

import com.squareup.otto.Subscribe;

import org.mariotaku.querybuilder.Columns.Column;
import org.mariotaku.querybuilder.Expression;
import org.mariotaku.querybuilder.RawItemArray;
import de.vanita5.twittnuker.activity.support.HomeActivity;
import de.vanita5.twittnuker.adapter.CursorStatusesAdapter;
import de.vanita5.twittnuker.loader.support.ExtendedCursorLoader;
import de.vanita5.twittnuker.provider.TwidereDataStore.Accounts;
import de.vanita5.twittnuker.provider.TwidereDataStore.Statuses;
import de.vanita5.twittnuker.util.AsyncTaskUtils;
import de.vanita5.twittnuker.util.Utils;
import de.vanita5.twittnuker.util.message.FavoriteCreatedEvent;
import de.vanita5.twittnuker.util.message.FavoriteDestroyedEvent;
import de.vanita5.twittnuker.util.message.GetStatusesTaskEvent;
import de.vanita5.twittnuker.util.message.StatusDestroyedEvent;
import de.vanita5.twittnuker.util.message.StatusListChangedEvent;
import de.vanita5.twittnuker.util.message.StatusRetweetedEvent;

import static de.vanita5.twittnuker.util.Utils.buildStatusFilterWhereClause;
import static de.vanita5.twittnuker.util.Utils.getNewestStatusIdsFromDatabase;
import static de.vanita5.twittnuker.util.Utils.getOldestStatusIdsFromDatabase;
import static de.vanita5.twittnuker.util.Utils.getTableNameByUri;

public abstract class CursorStatusesFragment extends AbsStatusesFragment<Cursor> {

    @Override
    protected void onLoadingFinished() {

    }

    private ContentObserver mContentObserver;

    public abstract Uri getContentUri();

	@Override
    protected Loader<Cursor> onCreateStatusesLoader(final Context context,
                                                 final Bundle args,
                                                 final boolean fromUser) {
        final Uri uri = getContentUri();
        final String table = getTableNameByUri(uri);
        final String sortOrder = getSortOrder();
        final long[] accountIds = getAccountIds();
        final Expression accountWhere = Expression.in(new Column(Statuses.ACCOUNT_ID), new RawItemArray(accountIds));
        final Expression filterWhere = getFiltersWhere(table), where;
        if (filterWhere != null) {
            where = Expression.and(accountWhere, filterWhere);
        } else {
            where = accountWhere;
        }
        final String selection = processWhere(where).getSQL();
        getAdapter().setShowAccountsColor(accountIds.length > 1);
        return new ExtendedCursorLoader(context, uri, Statuses.COLUMNS, selection, null, sortOrder, fromUser);
    }

    @Override
    protected Object createMessageBusCallback() {
        return new CursorStatusesBusCallback();
    }


    protected class CursorStatusesBusCallback {

		@Subscribe
        public void notifyGetStatusesTaskChanged(GetStatusesTaskEvent event) {
            if (!event.uri.equals(getContentUri())) return;
            setRefreshing(event.running);
            if (!event.running) {
                setLoadMoreIndicatorVisible(false);
                setRefreshEnabled(true);
            }
		}

        @Subscribe
        public void notifyFavoriteCreated(FavoriteCreatedEvent event) {
        }

        @Subscribe
        public void notifyFavoriteDestroyed(FavoriteDestroyedEvent event) {
        }

        @Subscribe
        public void notifyStatusDestroyed(StatusDestroyedEvent event) {
        }

        @Subscribe
        public void notifyStatusListChanged(StatusListChangedEvent event) {
            getAdapter().notifyDataSetChanged();
        }

        @Subscribe
        public void notifyStatusRetweeted(StatusRetweetedEvent event) {
        }

    }

    @Override
    protected long[] getAccountIds() {
        final Bundle args = getArguments();
        if (args != null && args.getLong(EXTRA_ACCOUNT_ID) > 0) {
            return new long[]{args.getLong(EXTRA_ACCOUNT_ID)};
        }
        final FragmentActivity activity = getActivity();
        if (activity instanceof HomeActivity) {
            return ((HomeActivity) activity).getActivatedAccountIds();
        }
        return Utils.getActivatedAccountIds(getActivity());
    }

    @Override
    public void onStart() {
        super.onStart();
        final ContentResolver cr = getContentResolver();
        mContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                reloadStatuses();
            }
        };
        cr.registerContentObserver(Accounts.CONTENT_URI, true, mContentObserver);
        updateRefreshState();
    }

    protected void reloadStatuses() {
        final Bundle args = new Bundle(), fragmentArgs = getArguments();
        if (fragmentArgs != null) {
            args.putAll(fragmentArgs);
            args.putBoolean(EXTRA_FROM_USER, true);
        }
        getLoaderManager().restartLoader(0, args, this);
    }

    @Override
    public void onStop() {
        final ContentResolver cr = getContentResolver();
        cr.unregisterContentObserver(mContentObserver);
        super.onStop();
    }

    @Override
    protected boolean hasMoreData(final Cursor cursor) {
        return cursor != null && cursor.getCount() != 0;
    }

    @NonNull
    @Override
	protected CursorStatusesAdapter onCreateAdapter(final Context context, final boolean compact) {
		return new CursorStatusesAdapter(context, compact);
	}

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        getAdapter().setData(null);
    }

    @Override
    public void onLoadMoreContents() {
        super.onLoadMoreContents();
        AsyncTaskUtils.executeTask(new AsyncTask<Object, Object, long[][]>() {

            @Override
            protected long[][] doInBackground(final Object... params) {
                final long[][] result = new long[3][];
                result[0] = getAccountIds();
                result[1] = getOldestStatusIds(result[0]);
                return result;
            }

            @Override
            protected void onPostExecute(final long[][] result) {
                getStatuses(result[0], result[1], result[2]);
            }

        });
    }

    @Override
    public boolean triggerRefresh() {
        AsyncTaskUtils.executeTask(new AsyncTask<Object, Object, long[][]>() {

            @Override
            protected long[][] doInBackground(final Object... params) {
                final long[][] result = new long[3][];
                result[0] = getAccountIds();
                result[2] = getNewestStatusIds(result[0]);
                return result;
            }

            @Override
            protected void onPostExecute(final long[][] result) {
                getStatuses(result[0], result[1], result[2]);
            }

        });
        return true;
    }

    protected Expression getFiltersWhere(String table) {
        if (!isFilterEnabled()) return null;
        return buildStatusFilterWhereClause(table, null);
    }

    protected long[] getNewestStatusIds(long[] accountIds) {
        return getNewestStatusIdsFromDatabase(getActivity(), getContentUri(), accountIds);
    }

    protected abstract int getNotificationType();

    protected long[] getOldestStatusIds(long[] accountIds) {
        return getOldestStatusIdsFromDatabase(getActivity(), getContentUri(), accountIds);
    }

    protected abstract boolean isFilterEnabled();

    protected Expression processWhere(final Expression where) {
        return where;
    }

    protected abstract void updateRefreshState();

    private String getSortOrder() {
        final SharedPreferences preferences = getSharedPreferences();
        final boolean sortById = preferences.getBoolean(KEY_SORT_TIMELINE_BY_ID, false);
        return sortById ? Statuses.SORT_ORDER_STATUS_ID_DESC : Statuses.SORT_ORDER_TIMESTAMP_DESC;
    }
}