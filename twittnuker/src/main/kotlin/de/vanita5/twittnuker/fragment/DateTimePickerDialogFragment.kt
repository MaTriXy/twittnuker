/*
 *  Twittnuker - Twitter client for Android
 *
 *  Copyright (C) 2013-2017 vanita5 <mail@vanit.as>
 *
 *  This program incorporates a modified version of Twidere.
 *  Copyright (C) 2012-2017 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.vanita5.twittnuker.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.ViewAnimator
import de.vanita5.twittnuker.R
import de.vanita5.twittnuker.extension.applyTheme
import de.vanita5.twittnuker.extension.displayedChildId
import java.util.*


class DateTimePickerDialogFragment : BaseDialogFragment() {

    private val listener: OnDateTimeSelectedListener? get() {
        return targetFragment as? OnDateTimeSelectedListener ?:
                parentFragment as? OnDateTimeSelectedListener ?:
                context as? OnDateTimeSelectedListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val builder = AlertDialog.Builder(context)
        builder.setView(R.layout.dialog_date_time_picker)
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.setNeutralButton(R.string.action_clear) { _, _ ->
            listener?.onDateCleared()
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            it as AlertDialog
            it.applyTheme()

            val positiveButton = it.getButton(DialogInterface.BUTTON_POSITIVE)

            val viewAnimator = it.findViewById(R.id.viewAnimator) as ViewAnimator
            val datePicker = it.findViewById(R.id.datePicker) as DatePicker
            val timePicker = it.findViewById(R.id.timePicker) as TimePicker
            val calendar = Calendar.getInstance()

            fun showTimePicker() {
                viewAnimator.displayedChildId = R.id.timePicker
                positiveButton.text = getText(android.R.string.ok)
                positiveButton.setOnClickListener {
                    finishSelection(calendar)
                }
            }

            datePicker.init(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)) { _, year, monthOfYear, dayOfMonth ->
                calendar.set(year, monthOfYear, dayOfMonth)
                showTimePicker()
            }
            timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
            }
            viewAnimator.displayedChildId = R.id.datePicker
            positiveButton.text = getText(R.string.action_next_step)

            positiveButton.setOnClickListener {
                showTimePicker()
            }
        }
        return dialog
    }

    private fun finishSelection(calendar: Calendar) {
        listener?.onDateSelected(calendar.time)
        dismiss()
    }

    interface OnDateTimeSelectedListener {
        fun onDateSelected(date: Date)
        fun onDateCleared()
    }
}