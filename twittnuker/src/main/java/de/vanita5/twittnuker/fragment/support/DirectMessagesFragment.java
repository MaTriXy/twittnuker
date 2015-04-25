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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.util.LongSparseArray;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.support.v7.widget.FixedLinearLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.mariotaku.querybuilder.Columns.Column;
import org.mariotaku.querybuilder.Expression;
import org.mariotaku.querybuilder.RawItemArray;
import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.activity.iface.IControlBarActivity;
import de.vanita5.twittnuker.activity.iface.IControlBarActivity.ControlBarOffsetListener;
import de.vanita5.twittnuker.activity.support.BaseAppCompatActivity;
import de.vanita5.twittnuker.adapter.MessageEntriesAdapter;
import de.vanita5.twittnuker.adapter.MessageEntriesAdapter.DirectMessageEntry;
import de.vanita5.twittnuker.adapter.MessageEntriesAdapter.MessageEntriesAdapterListener;
import de.vanita5.twittnuker.adapter.decorator.DividerItemDecoration;
import de.vanita5.twittnuker.app.TwittnukerApplication;
import de.vanita5.twittnuker.fragment.iface.RefreshScrollTopInterface;
import de.vanita5.twittnuker.provider.TwidereDataStore.Accounts;
import de.vanita5.twittnuker.provider.TwidereDataStore.DirectMessages;
import de.vanita5.twittnuker.provider.TwidereDataStore.DirectMessages.Inbox;
import de.vanita5.twittnuker.provider.TwidereDataStore.Statuses;
import de.vanita5.twittnuker.util.AsyncTaskUtils;
import de.vanita5.twittnuker.util.AsyncTwitterWrapper;
import de.vanita5.twittnuker.util.ContentListScrollListener;
import de.vanita5.twittnuker.util.ContentListScrollListener.ContentListSupport;
import de.vanita5.twittnuker.util.KeyboardShortcutsHandler;
import de.vanita5.twittnuker.util.KeyboardShortcutsHandler.KeyboardShortcutCallback;
import de.vanita5.twittnuker.util.MultiSelectManager;
import de.vanita5.twittnuker.util.RecyclerViewNavigationHelper;
import de.vanita5.twittnuker.util.ThemeUtils;
import de.vanita5.twittnuker.util.Utils;
import de.vanita5.twittnuker.util.content.SupportFragmentReloadCursorObserver;
import de.vanita5.twittnuker.util.message.GetMessagesTaskEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static de.vanita5.twittnuker.util.Utils.getActivatedAccountIds;
import static de.vanita5.twittnuker.util.Utils.openMessageConversation;

public class DirectMessagesFragment extends BaseSupportFragment implements LoaderCallbacks<Cursor>,
        RefreshScrollTopInterface, OnRefreshListener, MessageEntriesAdapterListener,
        ControlBarOffsetListener, ContentListSupport, KeyboardShortcutCallback {

	private final SupportFragmentReloadCursorObserver mReloadContentObserver = new SupportFragmentReloadCursorObserver(
			this, 0, this);
    private final LongSparseArray<Set<Long>> mUnreadCountsToRemove = new LongSparseArray<>();
    private final Set<Integer> mReadPositions = Collections.synchronizedSet(new HashSet<Integer>());
	private MultiSelectManager mMultiSelectManager;
	private SharedPreferences mPreferences;
    private RecyclerView mRecyclerView;
    private MessageEntriesAdapter mAdapter;
	private int mFirstVisibleItem;
    private RemoveUnreadCountsTask mRemoveUnreadCountsTask;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private View mProgressContainer;
    private LinearLayoutManager mLayoutManager;
    private RecyclerViewNavigationHelper mRecyclerViewNavigationHelper;
    private Rect mSystemWindowsInsets = new Rect();
    private int mControlBarOffsetPixels;

    @Override
    public MessageEntriesAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public boolean isRefreshing() {
        if (mSwipeRefreshLayout == null || mAdapter == null) return false;
        return mSwipeRefreshLayout.isRefreshing() || mAdapter.isLoadMoreIndicatorVisible();
    }

    public void setRefreshing(boolean refreshing) {
        if (mAdapter == null || refreshing == mSwipeRefreshLayout.isRefreshing()) return;
        mSwipeRefreshLayout.setRefreshing(refreshing && !mAdapter.isLoadMoreIndicatorVisible());
    }

    @Override
    public void onLoadMoreContents() {
        loadMoreMessages();
    }

    @Override
    public void setControlVisible(boolean visible) {
        final FragmentActivity activity = getActivity();
        if (activity instanceof BaseAppCompatActivity) {
            ((BaseAppCompatActivity) activity).setControlBarVisibleAnimate(visible);
        }
    }

    public final LongSparseArray<Set<Long>> getUnreadCountsToRemove() {
        return mUnreadCountsToRemove;
    }

    @Override
    public boolean handleKeyboardShortcutSingle(@NonNull final KeyboardShortcutsHandler handler,
                                                final int keyCode, @NonNull final KeyEvent event) {
        return false;
    }

	@Override
    public boolean handleKeyboardShortcutRepeat(@NonNull final KeyboardShortcutsHandler handler,
                                                final int keyCode, final int repeatCount,
                                                @NonNull final KeyEvent event) {
        return mRecyclerViewNavigationHelper.handleKeyboardShortcutRepeat(handler, keyCode, repeatCount, event);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof IControlBarActivity) {
            ((IControlBarActivity) activity).registerControlBarOffsetListener(this);
    	}
	}

	@Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recycler_view, container, false);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final View view = getView();
        if (view == null) throw new AssertionError();
        final TwittnukerApplication application = TwittnukerApplication.getInstance(getActivity());
        mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        final Context viewContext = view.getContext();
        mMultiSelectManager = getMultiSelectManager();
        mAdapter = new MessageEntriesAdapter(viewContext);
        mAdapter.setListener(this);
        mLayoutManager = new FixedLinearLayoutManager(viewContext);
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getUserAccentColor(viewContext));
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerViewNavigationHelper = new RecyclerViewNavigationHelper(mRecyclerView, mLayoutManager, mAdapter);

        final ContentListScrollListener scrollListener = new ContentListScrollListener(this);
        scrollListener.setTouchSlop(ViewConfiguration.get(viewContext).getScaledTouchSlop());
        // TODO remove scroll listener
        mRecyclerView.addOnScrollListener(scrollListener);

        final DividerItemDecoration itemDecoration = new DividerItemDecoration(viewContext, mLayoutManager.getOrientation());
        final Resources res = viewContext.getResources();
        final int decorPaddingLeft = res.getDimensionPixelSize(R.dimen.element_spacing_normal) * 3
                + res.getDimensionPixelSize(R.dimen.icon_size_status_profile_image);
        itemDecoration.setPadding(decorPaddingLeft, 0, 0, 0);
        itemDecoration.setDecorationEndOffset(1);
        mRecyclerView.addItemDecoration(itemDecoration);
        getLoaderManager().initLoader(0, null, this);
        setListShown(false);
    }

	@Override
	public void onStart() {
		super.onStart();
		final ContentResolver resolver = getContentResolver();
		resolver.registerContentObserver(Accounts.CONTENT_URI, true, mReloadContentObserver);
        final Bus bus = TwittnukerApplication.getInstance(getActivity()).getMessageBus();
        bus.register(this);
        mAdapter.updateReadState();
        updateRefreshState();
	}

	@Override
	public void onStop() {
        final Bus bus = TwittnukerApplication.getInstance(getActivity()).getMessageBus();
        bus.unregister(this);
		final ContentResolver resolver = getContentResolver();
		resolver.unregisterContentObserver(mReloadContentObserver);
		super.onStop();
	}

	@Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case MENU_COMPOSE: {
                openMessageConversation(getActivity(), -1, -1);
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBaseViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onBaseViewCreated(view, savedInstanceState);
        mProgressContainer = view.findViewById(R.id.progress_container);
        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_layout);
        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
    }

    @Override
    public void onDetach() {
        final FragmentActivity activity = getActivity();
        if (activity instanceof IControlBarActivity) {
            ((IControlBarActivity) activity).unregisterControlBarOffsetListener(this);
        }
        super.onDetach();
    }

    @Override
    public void setUserVisibleHint(final boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
    }

    @Override
    protected void fitSystemWindows(Rect insets) {
        super.fitSystemWindows(insets);
        mRecyclerView.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        mSystemWindowsInsets.set(insets);
        updateRefreshProgressOffset();
    }

    @Override
    public void onControlBarOffsetChanged(IControlBarActivity activity, float offset) {
        mControlBarOffsetPixels = Math.round(activity.getControlBarHeight() * (1 - offset));
        updateRefreshProgressOffset();
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        final Uri uri = DirectMessages.ConversationEntries.CONTENT_URI;
        final long account_id = getAccountId();
        final long[] account_ids = account_id > 0 ? new long[]{account_id} : getActivatedAccountIds(getActivity());
        final boolean no_account_selected = account_ids.length == 0;
//        setEmptyText(no_account_selected ? getString(R.string.no_account_selected) : null);
//        if (!no_account_selected) {
//            getListView().setEmptyView(null);
//        }
        final Expression account_where = Expression.in(new Column(Statuses.ACCOUNT_ID), new RawItemArray(account_ids));
        return new CursorLoader(getActivity(), uri, null, account_where.getSQL(), null, null);
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
        if (getActivity() == null) return;
        mFirstVisibleItem = -1;
        mAdapter.setCursor(cursor);
        mAdapter.setLoadMoreIndicatorVisible(false);
        mAdapter.setLoadMoreSupported(cursor != null && cursor.getCount() > 0);
        mSwipeRefreshLayout.setEnabled(true);
//        mAdapter.setShowAccountColor(getActivatedAccountIds(getActivity()).length > 1);
        setListShown(true);
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        mAdapter.setCursor(null);
    }

    @Override
    public void onEntryClick(int position, DirectMessageEntry entry) {
        Utils.openMessageConversation(getActivity(), entry.account_id, entry.conversation_id);
    }

    @Override
    public void onUserClick(int position, DirectMessageEntry entry) {
        Utils.openUserProfile(getActivity(), entry.account_id, entry.conversation_id, entry.screen_name, null);
    }

    @Subscribe
    public void onGetMessagesTaskChanged(GetMessagesTaskEvent event) {
        if (event.uri.equals(Inbox.CONTENT_URI) && !event.running) {
            setRefreshing(false);
            mAdapter.setLoadMoreIndicatorVisible(false);
            mSwipeRefreshLayout.setEnabled(true);
        }
    }

    @Override
    public void onRefresh() {
        triggerRefresh();
    }

    @Override
	public boolean scrollToStart() {
		final AsyncTwitterWrapper twitter = getTwitterWrapper();
		final int tabPosition = getTabPosition();
		if (twitter != null && tabPosition >= 0) {
			twitter.clearUnreadCountAsync(tabPosition);
		}
        mRecyclerView.smoothScrollToPosition(0);
        return true;
	}

	@Override
    public boolean triggerRefresh() {
        AsyncTaskUtils.executeTask(new AsyncTask<Object, Object, long[][]>() {

            @Override
            protected long[][] doInBackground(final Object... params) {
                final long[][] result = new long[2][];
                result[0] = Utils.getActivatedAccountIds(getActivity());
                result[1] = Utils.getNewestMessageIdsFromDatabase(getActivity(), DirectMessages.Inbox.CONTENT_URI);
                return result;
            }

            @Override
            protected void onPostExecute(final long[][] result) {
                final AsyncTwitterWrapper twitter = getTwitterWrapper();
                if (twitter == null) return;
                twitter.getReceivedDirectMessagesAsync(result[0], null, result[1]);
                twitter.getSentDirectMessagesAsync(result[0], null, null);
            }

        });
        return true;
    }

	protected long getAccountId() {
		final Bundle args = getArguments();
		return args != null ? args.getLong(EXTRA_ACCOUNT_ID, -1) : -1;
	}

    protected void updateRefreshState() {
        final AsyncTwitterWrapper twitter = getTwitterWrapper();
        setRefreshing(twitter != null && (twitter.isReceivedDirectMessagesRefreshing() || twitter.isSentDirectMessagesRefreshing()));
    }

	private void addReadPosition(final int firstVisibleItem) {
		if (mFirstVisibleItem != firstVisibleItem) {
			mReadPositions.add(firstVisibleItem);
		}
		mFirstVisibleItem = firstVisibleItem;
	}

    private void addUnreadCountsToRemove(final long accountId, final long id) {
        if (mUnreadCountsToRemove.indexOfKey(accountId) < 0) {
            final Set<Long> counts = new HashSet<>();
			counts.add(id);
            mUnreadCountsToRemove.put(accountId, counts);
		} else {
            final Set<Long> counts = mUnreadCountsToRemove.get(accountId);
			counts.add(id);
		}
	}

//
    private void loadMoreMessages() {
        if (isRefreshing()) return;
        mAdapter.setLoadMoreIndicatorVisible(true);
        mSwipeRefreshLayout.setEnabled(false);
        AsyncTaskUtils.executeTask(new AsyncTask<Object, Object, long[][]>() {

            @Override
            protected long[][] doInBackground(final Object... params) {
                final long[][] result = new long[3][];
                result[0] = Utils.getActivatedAccountIds(getActivity());
                result[1] = Utils.getOldestMessageIdsFromDatabase(getActivity(), DirectMessages.Inbox.CONTENT_URI);
                result[2] = Utils.getOldestMessageIdsFromDatabase(getActivity(), DirectMessages.Outbox.CONTENT_URI);
                return result;
            }

            @Override
            protected void onPostExecute(final long[][] result) {
                final AsyncTwitterWrapper twitter = getTwitterWrapper();
                if (twitter == null) return;
                twitter.getReceivedDirectMessagesAsync(result[0], result[1], null);
                twitter.getSentDirectMessagesAsync(result[0], result[2], null);
            }

        });
    }

	private void removeUnreadCounts() {
        if (mRemoveUnreadCountsTask != null && mRemoveUnreadCountsTask.getStatus() == AsyncTask.Status.RUNNING)
            return;
		mRemoveUnreadCountsTask = new RemoveUnreadCountsTask(mReadPositions, this);
        AsyncTaskUtils.executeTask(mRemoveUnreadCountsTask);
	}

    private void setListShown(boolean shown) {
        mProgressContainer.setVisibility(shown ? View.GONE : View.VISIBLE);
        mSwipeRefreshLayout.setVisibility(shown ? View.VISIBLE : View.GONE);
    }

    private void updateRefreshProgressOffset() {
        if (mSystemWindowsInsets.top == 0 || mSwipeRefreshLayout == null || isRefreshing()) return;
        final float density = getResources().getDisplayMetrics().density;
        final int progressCircleDiameter = mSwipeRefreshLayout.getProgressCircleDiameter();
        final int swipeStart = (mSystemWindowsInsets.top - mControlBarOffsetPixels) - progressCircleDiameter;
        // 64: SwipeRefreshLayout.DEFAULT_CIRCLE_TARGET
        final int swipeDistance = Math.round(64 * density);
        mSwipeRefreshLayout.setProgressViewOffset(true, swipeStart, swipeStart + swipeDistance);
    }

    static class RemoveUnreadCountsTask extends AsyncTask<Object, Object, Object> {
		private final Set<Integer> read_positions;
        private final MessageEntriesAdapter adapter;
		private final DirectMessagesFragment fragment;

		RemoveUnreadCountsTask(final Set<Integer> read_positions, final DirectMessagesFragment fragment) {
            this.read_positions = Collections.synchronizedSet(new HashSet<>(read_positions));
			this.fragment = fragment;
            adapter = fragment.getAdapter();
		}

		@Override
        protected Object doInBackground(final Object... params) {
			for (final int pos : read_positions) {
                final DirectMessageEntry entry = adapter.getEntry(pos);
                final long id = entry.conversation_id, account_id = entry.account_id;
				fragment.addUnreadCountsToRemove(account_id, id);
			}
			return null;
		}

		@Override
        protected void onPostExecute(final Object result) {
			final AsyncTwitterWrapper twitter = fragment.getTwitterWrapper();
			if (twitter != null) {
				twitter.removeUnreadCountsAsync(fragment.getTabPosition(), fragment.getUnreadCountsToRemove());
			}
		}

	}

}