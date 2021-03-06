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

package de.vanita5.twittnuker.loader

import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import android.support.v4.content.FixedAsyncTaskLoader
import org.mariotaku.ktextension.addOnAccountsUpdatedListenerSafe
import org.mariotaku.ktextension.removeOnAccountsUpdatedListenerSafe
import de.vanita5.twittnuker.model.AccountDetails
import de.vanita5.twittnuker.model.util.AccountUtils
import java.lang.ref.WeakReference

class AccountDetailsLoader(
        context: Context,
        val filter: (AccountDetails.() -> Boolean)? = null
) : FixedAsyncTaskLoader<List<AccountDetails>>(context) {

    private var accountUpdateListener: OnAccountsUpdateListener? = null
        set(value) {
            val am: AccountManager = AccountManager.get(context)
            field?.let {
                am.removeOnAccountsUpdatedListenerSafe(it)
            }
            if (value != null) {
                am.addOnAccountsUpdatedListenerSafe(value, updateImmediately = true)
            }
        }

    override fun loadInBackground(): List<AccountDetails> {
        val am: AccountManager = AccountManager.get(context)
        return AccountUtils.getAllAccountDetails(am, true).filter {
            filter?.invoke(it) ?: true
        }.sortedBy(AccountDetails::position)
    }

    override fun onReset() {
        super.onReset()
        onStopLoading()
        accountUpdateListener = null
    }

    override fun onStartLoading() {
        val weakThis = WeakReference(this)
        accountUpdateListener = OnAccountsUpdateListener {
            weakThis.get()?.onContentChanged()
        }
        if (takeContentChanged()) {
            forceLoad()
        }
    }

    override fun onStopLoading() {
        cancelLoad()
    }
}