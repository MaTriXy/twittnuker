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
import android.content.SharedPreferences
import android.support.v4.text.BidiFormatter
import android.support.v7.widget.RecyclerView
import com.bumptech.glide.RequestManager
import org.mariotaku.kpreferences.get
import de.vanita5.twittnuker.R
import de.vanita5.twittnuker.adapter.iface.IContentAdapter
import de.vanita5.twittnuker.constant.displayProfileImageKey
import de.vanita5.twittnuker.constant.profileImageStyleKey
import de.vanita5.twittnuker.constant.showAbsoluteTimeKey
import de.vanita5.twittnuker.constant.textSizeKey
import de.vanita5.twittnuker.model.DefaultFeatures
import de.vanita5.twittnuker.util.*
import de.vanita5.twittnuker.util.dagger.GeneralComponent

import javax.inject.Inject

abstract class BaseRecyclerViewAdapter<VH : RecyclerView.ViewHolder>(
        val context: Context,
        override val requestManager: RequestManager
) : RecyclerView.Adapter<VH>(), IContentAdapter {

    @Inject
    override final lateinit var twitterWrapper: AsyncTwitterWrapper

    @Inject
    override final lateinit var userColorNameManager: UserColorNameManager
    @Inject
    override final lateinit var bidiFormatter: BidiFormatter
    @Inject
    lateinit var preferences: SharedPreferences
    @Inject
    lateinit var readStateManager: ReadStateManager
    @Inject
    lateinit var multiSelectManager: MultiSelectManager
    @Inject
    lateinit var defaultFeatures: DefaultFeatures

    override final val profileImageSize: String = context.getString(R.string.profile_image_size)
    override final val profileImageStyle: Int
    override final val textSize: Float
    override final val profileImageEnabled: Boolean
    override final val showAbsoluteTime: Boolean

    init {
        @Suppress("UNCHECKED_CAST")
        GeneralComponent.get(context).inject(this as BaseRecyclerViewAdapter<RecyclerView.ViewHolder>)
        profileImageStyle = preferences[profileImageStyleKey]
        textSize = preferences[textSizeKey].toFloat()
        profileImageEnabled = preferences[displayProfileImageKey]
        showAbsoluteTime = preferences[showAbsoluteTimeKey]
    }

}