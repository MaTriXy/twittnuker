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

package de.vanita5.twittnuker.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.bumptech.glide.RequestManager
import de.vanita5.twittnuker.R
import de.vanita5.twittnuker.adapter.iface.ILoadMoreSupportAdapter
import de.vanita5.twittnuker.alias.ItemClickListener
import de.vanita5.twittnuker.model.ParcelableHashtag
import de.vanita5.twittnuker.model.ParcelableStatus
import de.vanita5.twittnuker.model.ParcelableUser
import de.vanita5.twittnuker.model.ParcelableUserList
import de.vanita5.twittnuker.util.StatusAdapterLinkClickHandler
import de.vanita5.twittnuker.util.TwidereLinkify
import de.vanita5.twittnuker.view.holder.HashtagViewHolder
import de.vanita5.twittnuker.view.holder.StatusViewHolder
import de.vanita5.twittnuker.view.holder.UserListViewHolder
import de.vanita5.twittnuker.view.holder.UserViewHolder

class VariousItemsAdapter(
        context: Context,
        requestManager: RequestManager
) : LoadMoreSupportAdapter<RecyclerView.ViewHolder>(context, requestManager) {

    private val inflater = LayoutInflater.from(context)
    val dummyAdapter: DummyItemAdapter
    var hashtagClickListener: ItemClickListener? = null

    private var data: List<*>? = null

    init {
        val handler = StatusAdapterLinkClickHandler<Any>(context,
                preferences)
        dummyAdapter = DummyItemAdapter(context, TwidereLinkify(handler), this, requestManager)
        handler.setAdapter(dummyAdapter)
        dummyAdapter.updateOptions()
        loadMoreIndicatorPosition = ILoadMoreSupportAdapter.NONE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            VIEW_TYPE_STATUS -> {
                return ListParcelableStatusesAdapter.createStatusViewHolder(dummyAdapter,
                        inflater, parent)
            }
            VIEW_TYPE_USER -> {
                return ParcelableUsersAdapter.createUserViewHolder(dummyAdapter, inflater, parent)
            }
            VIEW_TYPE_USER_LIST -> {
                return ParcelableUserListsAdapter.createUserListViewHolder(dummyAdapter, inflater,
                        parent)
            }
            VIEW_TYPE_HASHTAG -> {
                val view = inflater.inflate(R.layout.list_item_two_line_small, parent, false)
                val holder = HashtagViewHolder(view, hashtagClickListener)
                return holder
            }
        }
        throw UnsupportedOperationException()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val obj = getItem(position)
        when (holder.itemViewType) {
            VIEW_TYPE_STATUS -> {
                (holder as StatusViewHolder).display(obj as ParcelableStatus,
                        displayInReplyTo = true)
            }
            VIEW_TYPE_USER -> {
                (holder as UserViewHolder).display(obj as ParcelableUser)
            }
            VIEW_TYPE_USER_LIST -> {
                (holder as UserListViewHolder).display(obj as ParcelableUserList)
            }
            VIEW_TYPE_HASHTAG -> {
                (holder as HashtagViewHolder).display(obj as ParcelableHashtag)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return getItemViewType(getItem(position))
    }

    private fun getItemViewType(obj: Any): Int {
        when (obj) {
            is ParcelableStatus -> return VIEW_TYPE_STATUS
            is ParcelableUser -> return VIEW_TYPE_USER
            is ParcelableUserList -> return VIEW_TYPE_USER_LIST
            is ParcelableHashtag -> return VIEW_TYPE_HASHTAG
            else -> throw UnsupportedOperationException("Unsupported object " + obj)
        }
    }

    fun setData(data: List<*>?) {
        this.data = data
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        if (data == null) return 0
        return data!!.size
    }

    fun getItem(position: Int): Any {
        return data!![position]!!
    }

    companion object {

        val VIEW_TYPE_STATUS = 1
        val VIEW_TYPE_USER = 2
        val VIEW_TYPE_USER_LIST = 3
        val VIEW_TYPE_HASHTAG = 4
    }
}