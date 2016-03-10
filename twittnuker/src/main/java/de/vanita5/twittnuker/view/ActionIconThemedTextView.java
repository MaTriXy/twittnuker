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

package de.vanita5.twittnuker.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.util.support.TextViewSupport;

public class ActionIconThemedTextView extends AppCompatTextView {

    private int mIconWidth, mIconHeight;
    @ColorInt
    private int mColor;
    @ColorInt
    private int mDisabledColor;
    @ColorInt
    private int mActivatedColor;

    public ActionIconThemedTextView(Context context) {
        super(context);
    }

    public ActionIconThemedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public ActionIconThemedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public void init(Context context, AttributeSet attrs) {
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.IconActionButton,
                R.attr.cardActionButtonStyle, R.style.Widget_CardActionButton);
        mColor = a.getColor(R.styleable.IconActionButton_iabColor, 0);
        mDisabledColor = a.getColor(R.styleable.IconActionButton_iabDisabledColor, 0);
        mActivatedColor = a.getColor(R.styleable.IconActionButton_iabActivatedColor, 0);
        mIconWidth = a.getDimensionPixelSize(R.styleable.IconActionButton_iabIconWidth, 0);
        mIconHeight = a.getDimensionPixelSize(R.styleable.IconActionButton_iabIconHeight, 0);
        a.recycle();
    }

    @ColorInt
    public int getColor() {
        if (mColor != 0) return mColor;
        final ColorStateList colors = getTextColors();
        if (colors != null) return colors.getDefaultColor();
        return getCurrentTextColor();
    }

    public void setColor(@ColorInt int color) {
        this.mColor = color;
        refreshDrawableState();
    }

    @ColorInt
    public int getActivatedColor() {
        if (mActivatedColor != 0) return mActivatedColor;
        final ColorStateList colors = getLinkTextColors();
        if (colors != null) return colors.getDefaultColor();
        return getCurrentTextColor();
    }

    public void setActivatedColor(@ColorInt int color) {
        this.mActivatedColor = color;
        refreshDrawableState();
    }

    @ColorInt
    public int getDisabledColor() {
        if (mDisabledColor != 0) return mDisabledColor;
        final ColorStateList colors = getTextColors();
        if (colors != null) return colors.getColorForState(new int[0], colors.getDefaultColor());
        return getCurrentTextColor();
    }

    public void setDisabledColor(@ColorInt int color) {
        this.mDisabledColor = color;
        refreshDrawableState();
    }

    @Override
    public void refreshDrawableState() {
        updateCompoundDrawables();
        super.refreshDrawableState();
    }

    private void updateCompoundDrawables() {
        for (Drawable d : TextViewSupport.getCompoundDrawablesRelative(this)) {
            if (d == null) continue;
            d.mutate();
            final int color;
            if (isActivated()) {
                color = getActivatedColor();
            } else if (isEnabled()) {
                color = getColor();
            } else {
                color = getDisabledColor();
            }
            if (mIconWidth > 0 && mIconHeight > 0) {
                d.setBounds(0, 0, mIconWidth, mIconHeight);
            }
            d.setColorFilter(color, Mode.SRC_ATOP);
        }
    }

}