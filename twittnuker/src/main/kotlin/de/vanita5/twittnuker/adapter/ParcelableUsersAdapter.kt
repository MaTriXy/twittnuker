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

package de.vanita5.twittnuker.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup

import de.vanita5.twittnuker.Constants
import de.vanita5.twittnuker.R
import de.vanita5.twittnuker.adapter.iface.ILoadMoreSupportAdapter
import de.vanita5.twittnuker.adapter.iface.ILoadMoreSupportAdapter.Companion.ITEM_VIEW_TYPE_LOAD_INDICATOR
import de.vanita5.twittnuker.adapter.iface.IUsersAdapter
import de.vanita5.twittnuker.constant.SharedPreferenceConstants
import de.vanita5.twittnuker.model.ParcelableUser
import de.vanita5.twittnuker.model.UserKey
import de.vanita5.twittnuker.util.Utils
import de.vanita5.twittnuker.view.holder.LoadIndicatorViewHolder
import de.vanita5.twittnuker.view.holder.UserViewHolder

class ParcelableUsersAdapter(context: Context) : LoadMoreSupportAdapter<RecyclerView.ViewHolder>(context), Constants, IUsersAdapter<List<ParcelableUser>> {
    private val inflater: LayoutInflater
    private var data: List<ParcelableUser>? = null

    override val showAccountsColor: Boolean = false
    override val profileImageStyle: Int
    override val textSize: Float
    override val profileImageEnabled: Boolean
    override val isShowAbsoluteTime: Boolean
    override var userClickListener: IUsersAdapter.UserClickListener? = null
    override var requestClickListener: IUsersAdapter.RequestClickListener? = null
    override var followClickListener: IUsersAdapter.FriendshipClickListener? = null
    override var simpleLayout: Boolean = false

    init {
        inflater = LayoutInflater.from(context)
        textSize = preferences.getInt(SharedPreferenceConstants.KEY_TEXT_SIZE, context.resources.getInteger(R.integer.default_text_size)).toFloat()
        profileImageStyle = Utils.getProfileImageStyle(preferences.getString(SharedPreferenceConstants.KEY_PROFILE_IMAGE_STYLE, null))
        profileImageEnabled = preferences.getBoolean(SharedPreferenceConstants.KEY_DISPLAY_PROFILE_IMAGE)
        isShowAbsoluteTime = preferences.getBoolean(SharedPreferenceConstants.KEY_SHOW_ABSOLUTE_TIME)
    }

    fun getData(): List<ParcelableUser>? {
        return data
    }

    override fun setData(data: List<ParcelableUser>?): Boolean {
        this.data = data
        notifyDataSetChanged()
        return true
    }

    private fun bindUser(holder: UserViewHolder, position: Int) {
        holder.displayUser(getUser(position)!!)
    }

    override fun getItemCount(): Int {
        val position = loadMoreIndicatorPosition
        var count = userCount
        if (position and ILoadMoreSupportAdapter.START !== 0L) {
            count++
        }
        if (position and ILoadMoreSupportAdapter.END !== 0L) {
            count++
        }
        return count
    }

    override fun getUser(position: Int): ParcelableUser? {
        val dataPosition = position - userStartIndex
        if (dataPosition < 0 || dataPosition >= userCount) return null
        return data!![dataPosition]
    }

    val userStartIndex: Int
        get() {
            val position = loadMoreIndicatorPosition
            var start = 0
            if (position and ILoadMoreSupportAdapter.START !== 0L) {
                start += 1
            }
            return start
        }

    override fun getUserId(position: Int): String? {
        if (position == userCount) return null
        return data!![position].key.id
    }

    override val userCount: Int
        get() {
            if (data == null) return 0
            return data!!.size
        }

    fun removeUserAt(position: Int): Boolean {
        val data = this.data as? MutableList ?: return false
        val dataPosition = position - userStartIndex
        if (dataPosition < 0 || dataPosition >= userCount) return false
        data.removeAt(dataPosition)
        notifyItemRemoved(position)
        return true
    }

    fun setUserAt(position: Int, user: ParcelableUser): Boolean {
        val data = this.data as? MutableList ?: return false
        val dataPosition = position - userStartIndex
        if (dataPosition < 0 || dataPosition >= userCount) return false
        data[dataPosition] = user
        notifyItemChanged(position)
        return true
    }

    fun findPosition(accountKey: UserKey, userKey: UserKey): Int {
        if (data == null) return RecyclerView.NO_POSITION
        for (i in userStartIndex until userStartIndex + userCount) {
            val user = data!![i]
            if (accountKey == user.account_key && userKey == user.key) {
                return i
            }
        }
        return RecyclerView.NO_POSITION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            ITEM_VIEW_TYPE_USER -> {
                return createUserViewHolder(this, inflater, parent)
            }
            ITEM_VIEW_TYPE_LOAD_INDICATOR -> {
                val view = inflater.inflate(R.layout.card_item_load_indicator, parent, false)
                return LoadIndicatorViewHolder(view)
            }
        }
        throw IllegalStateException("Unknown view type " + viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            ITEM_VIEW_TYPE_USER -> {
                bindUser(holder as UserViewHolder, position)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (loadMoreIndicatorPosition and ILoadMoreSupportAdapter.START !== 0L && position == 0) {
            return ITEM_VIEW_TYPE_LOAD_INDICATOR
        }
        if (position == userCount) {
            return ITEM_VIEW_TYPE_LOAD_INDICATOR
        }
        return ITEM_VIEW_TYPE_USER
    }

    companion object {

        const val ITEM_VIEW_TYPE_USER = 2


        fun createUserViewHolder(adapter: IUsersAdapter<*>, inflater: LayoutInflater, parent: ViewGroup): UserViewHolder {
            val view = inflater.inflate(R.layout.list_item_user, parent, false)
            val holder = UserViewHolder(view, adapter, adapter.simpleLayout)
            holder.setOnClickListeners()
            holder.setupViewOptions()
            return holder
        }
    }
}