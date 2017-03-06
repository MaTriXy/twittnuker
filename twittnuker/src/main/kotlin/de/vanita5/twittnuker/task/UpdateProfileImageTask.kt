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

package de.vanita5.twittnuker.task

import android.content.Context
import android.net.Uri
import android.util.Log
import de.vanita5.twittnuker.library.MicroBlogException
import de.vanita5.twittnuker.R
import de.vanita5.twittnuker.TwittnukerConstants
import de.vanita5.twittnuker.model.ParcelableUser
import de.vanita5.twittnuker.model.SingleResponse
import de.vanita5.twittnuker.model.UserKey
import de.vanita5.twittnuker.model.event.ProfileUpdatedEvent
import de.vanita5.twittnuker.model.util.ParcelableUserUtils
import de.vanita5.twittnuker.util.MicroBlogAPIFactory
import de.vanita5.twittnuker.util.TwitterWrapper
import de.vanita5.twittnuker.util.Utils
import java.io.IOException

open class UpdateProfileImageTask<ResultHandler>(
        context: Context,
        private val accountKey: UserKey,
        private val imageUri: Uri,
        private val deleteImage: Boolean
) : BaseAbstractTask<Any?, SingleResponse<ParcelableUser>, ResultHandler>(context) {

    private val profileImageSize = context.getString(R.string.profile_image_size)

    override fun doLongOperation(params: Any?): SingleResponse<ParcelableUser> {
        try {
            val microBlog = MicroBlogAPIFactory.getInstance(context, accountKey)
                    ?: throw MicroBlogException("No account")
            TwitterWrapper.updateProfileImage(context, microBlog, imageUri, deleteImage)
            // Wait for 5 seconds, see
            // https://dev.twitter.com/rest/reference/post/account/update_profile_image
            try {
                Thread.sleep(5000L)
            } catch (e: InterruptedException) {
                Log.w(TwittnukerConstants.LOGTAG, e)
            }

            val user = microBlog.verifyCredentials()
            return SingleResponse(ParcelableUserUtils.fromUser(user, accountKey,
                    profileImageSize = profileImageSize))
        } catch (e: MicroBlogException) {
            return SingleResponse(exception = e)
        } catch (e: IOException) {
            return SingleResponse(exception = e)
        }

    }

    override fun afterExecute(handler: ResultHandler?, result: SingleResponse<ParcelableUser>) {
        super.afterExecute(handler, result)
        if (result.hasData()) {
            Utils.showOkMessage(context, R.string.profile_image_updated, false)
            bus.post(ProfileUpdatedEvent(result.data!!))
        } else {
            Utils.showErrorMessage(context, R.string.action_updating_profile_image, result.exception, true)
        }
    }

}