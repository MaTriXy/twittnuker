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

package de.vanita5.twittnuker.extension.model.api.mastodon

import org.mariotaku.ktextension.mapToArray
import de.vanita5.microblog.library.mastodon.model.Notification
import de.vanita5.microblog.library.mastodon.model.Relationship
import de.vanita5.microblog.library.twitter.model.Activity
import de.vanita5.twittnuker.extension.model.toLite
import de.vanita5.twittnuker.extension.model.toSummaryLine
import de.vanita5.twittnuker.model.AccountDetails
import de.vanita5.twittnuker.model.ParcelableActivity
import de.vanita5.twittnuker.model.ParcelableUser
import de.vanita5.twittnuker.model.UserKey

fun Notification.toParcelable(details: AccountDetails, relationships: Map<String, Relationship>?):
        ParcelableActivity {
    return toParcelable(details.key, relationships).apply {
        account_color = details.color
    }
}

fun Notification.toParcelable(accountKey: UserKey, relationships: Map<String, Relationship>?):
        ParcelableActivity {
    val result = ParcelableActivity()
    result.account_key = accountKey
    result.id = "$id-$id"
    result.timestamp = createdAt.time
    result.min_position = id
    result.max_position = id
    result.min_sort_position = result.timestamp
    result.max_sort_position = result.timestamp

    result.sources = toSources(accountKey, relationships)
    result.user_key = result.sources?.firstOrNull()?.key ?: UserKey("multiple", null)

    val status = this.status
    when (type) {
        Notification.Type.MENTION -> {
            if (status == null) {
                result.action = Activity.Action.INVALID
                return result
            }
            result.action = Activity.Action.MENTION
            status.applyTo(accountKey, result)
        }
        Notification.Type.REBLOG -> {
            if (status == null) {
                result.action = Activity.Action.INVALID
                return result
            }
            result.action = Activity.Action.RETWEET
            val parcelableStatus = status.toParcelable(accountKey)
            result.target_objects = ParcelableActivity.RelatedObject.statuses(parcelableStatus)
            result.summary_line = arrayOf(parcelableStatus.toSummaryLine())
        }
        Notification.Type.FAVOURITE -> {
            if (status == null) {
                result.action = Activity.Action.INVALID
                return result
            }
            result.action = Activity.Action.FAVORITE
            val parcelableStatus = status.toParcelable(accountKey)
            result.targets = ParcelableActivity.RelatedObject.statuses(parcelableStatus)
            result.summary_line = arrayOf(parcelableStatus.toSummaryLine())
        }
        Notification.Type.FOLLOW -> {
            result.action = Activity.Action.FOLLOW
        }
        else -> {
            result.action = type
        }
    }

    result.sources_lite = result.sources?.mapToArray { it.toLite() }
    result.source_keys = result.sources_lite?.mapToArray { it.key }

    return result
}

private fun Notification.toSources(accountKey: UserKey, relationships: Map<String, Relationship>?):
        Array<ParcelableUser>? {
    val account = this.account ?: return null
    val relationship = relationships?.get(account.id)
    return arrayOf(account.toParcelable(accountKey, relationship = relationship))
}
