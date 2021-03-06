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

package de.vanita5.twittnuker.fragment.status

import android.accounts.AccountManager
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AlertDialog.Builder
import android.view.View
import android.widget.Toast
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.combine.and
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.promiseOnUi
import nl.komponents.kovenant.ui.successUi
import de.vanita5.microblog.library.MicroBlog
import de.vanita5.twittnuker.R
import de.vanita5.twittnuker.adapter.DummyItemAdapter
import de.vanita5.twittnuker.constant.IntentConstants.*
import de.vanita5.twittnuker.extension.applyTheme
import de.vanita5.twittnuker.extension.model.api.toParcelable
import de.vanita5.twittnuker.extension.model.newMicroBlogInstance
import de.vanita5.twittnuker.extension.onShow
import de.vanita5.twittnuker.fragment.BaseDialogFragment
import de.vanita5.twittnuker.model.AccountDetails
import de.vanita5.twittnuker.model.ParcelableStatus
import de.vanita5.twittnuker.model.UserKey
import de.vanita5.twittnuker.model.util.AccountUtils
import de.vanita5.twittnuker.view.holder.StatusViewHolder
import java.lang.ref.WeakReference

abstract class AbsStatusDialogFragment : BaseDialogFragment() {

    protected abstract val Dialog.loadProgress: View
    protected abstract val Dialog.itemContent: View

    protected val status: ParcelableStatus?
        get() = arguments.getParcelable<ParcelableStatus>(EXTRA_STATUS)

    protected val statusId: String
        get() = arguments.getString(EXTRA_STATUS_ID)

    protected val accountKey: UserKey
        get() = arguments.getParcelable(EXTRA_ACCOUNT_KEY)

    private lateinit var adapter: DummyItemAdapter

    override final fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = Builder(context)
        val accountKey = this.accountKey

        builder.setupAlertDialog()

        adapter = DummyItemAdapter(context, requestManager = requestManager)
        adapter.showCardActions = false
        adapter.showAccountsColor = true

        val dialog = builder.create()
        dialog.onShow {
            val context = it.context ?: return@onShow
            it.applyTheme()

            val am = AccountManager.get(context)
            val details = AccountUtils.getAccountDetails(am, accountKey, true) ?: run {
                dismiss()
                return@onShow
            }
            val weakThis = WeakReference(this)
            val weakHolder = WeakReference(StatusViewHolder(adapter = adapter, itemView = it.itemContent).apply {
                setupViewOptions()
            })
            val extraStatus = status
            if (extraStatus != null) {
                showStatus(weakHolder.get()!!, extraStatus, details, savedInstanceState)
            } else promiseOnUi {
                weakThis.get()?.showProgress()
            } and AbsStatusDialogFragment.showStatus(context, details, statusId, extraStatus).successUi { status ->
                val holder = weakHolder.get() ?: return@successUi
                weakThis.get()?.showStatus(holder, status, details, savedInstanceState)
            }.failUi {
                val fragment = weakThis.get()?.takeIf { it.dialog != null } ?: return@failUi
                Toast.makeText(fragment.context, R.string.message_toast_error_occurred,
                        Toast.LENGTH_SHORT).show()
                fragment.dismiss()
            }
        }
        return dialog
    }

    private fun showProgress() {
        val currentDialog = this.dialog as? AlertDialog ?: return
        currentDialog.loadProgress.visibility = View.VISIBLE
        currentDialog.itemContent.visibility = View.GONE
        currentDialog.getButton(BUTTON_POSITIVE)?.isEnabled = false
        currentDialog.getButton(BUTTON_NEUTRAL)?.isEnabled = false
    }

    private fun showStatus(holder: StatusViewHolder, status: ParcelableStatus,
                           details: AccountDetails, savedInstanceState: Bundle?) {
        status.apply {
            if (account_key != details.key) {
                my_retweet_id = null
                is_favorite = false
            }
            account_key = details.key
            account_color = details.color
        }
        val currentDialog = this.dialog as? AlertDialog ?: return
        currentDialog.getButton(BUTTON_POSITIVE)?.isEnabled = true
        currentDialog.getButton(BUTTON_NEUTRAL)?.isEnabled = true
        currentDialog.itemContent.visibility = View.VISIBLE
        currentDialog.loadProgress.visibility = View.GONE
        currentDialog.itemContent.isFocusable = false
        holder.display(status = status, displayInReplyTo = false)
        currentDialog.onStatusLoaded(details, status, savedInstanceState)
    }

    protected abstract fun Builder.setupAlertDialog()

    protected abstract fun AlertDialog.onStatusLoaded(account: AccountDetails, status: ParcelableStatus,
            savedInstanceState: Bundle?)

    companion object {

        fun showStatus(context: Context, details: AccountDetails, statusId: String,
                status: ParcelableStatus?): Promise<ParcelableStatus, Exception> {
            if (status != null) {
                return Promise.ofSuccess(status)
            }
            val microBlog = details.newMicroBlogInstance(context, MicroBlog::class.java)
            val profileImageSize = context.getString(R.string.profile_image_size)
            return task { microBlog.showStatus(statusId).toParcelable(details, profileImageSize) }
        }

    }
}