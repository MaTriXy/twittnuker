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

package de.vanita5.twittnuker.activity.support;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;

import de.vanita5.twittnuker.Constants;
import de.vanita5.twittnuker.activity.iface.IThemedActivity;
import de.vanita5.twittnuker.util.ColorUtils;
import de.vanita5.twittnuker.util.StrictModeUtils;
import de.vanita5.twittnuker.util.ThemeUtils;
import de.vanita5.twittnuker.util.Utils;
import de.vanita5.twittnuker.view.ShapedImageView;
import de.vanita5.twittnuker.view.ShapedImageView.ShapeStyle;

import static de.vanita5.twittnuker.util.Utils.restartActivity;

public abstract class ThemedFragmentActivity extends FragmentActivity implements Constants, IThemedActivity {

	private int mCurrentThemeResource, mCurrentThemeColor, mCurrentThemeBackgroundAlpha,
            mCurrentActionBarColor;
	@ShapeStyle
	private int mProfileImageStyle;
    private String mCurrentThemeBackgroundOption;

	@Override
    public final int getCurrentThemeResourceId() {
        return mCurrentThemeResource;
    }

    @Override
	public Resources getDefaultResources() {
		return super.getResources();
	}

    @Override
    public int getThemeBackgroundAlpha() {
        return ThemeUtils.getUserThemeBackgroundAlpha(this);
    }

    @Override
    public int getCurrentThemeBackgroundAlpha() {
        return mCurrentThemeBackgroundAlpha;
    }

    @Override
    public String getCurrentThemeBackgroundOption() {
        return mCurrentThemeBackgroundOption;
    }

    @Override
    public String getThemeBackgroundOption() {
        return ThemeUtils.getThemeBackgroundOption(this);
    }

    @Override
    public int getCurrentThemeColor() {
        return mCurrentThemeColor;
    }

    @Override
	public String getThemeFontFamily() {
		return ThemeUtils.getThemeFontFamily(this);
	}

	@Override
	public void navigateUpFromSameTask() {
		NavUtils.navigateUpFromSameTask(this);
	}

	@Override
	public final void restart() {
		restartActivity(this);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		if (Utils.isDebugBuild()) {
			StrictModeUtils.detectAllVmPolicy();
			StrictModeUtils.detectAllThreadPolicy();
		}
		setTheme();
		super.onCreate(savedInstanceState);
	}

	@Override
    public View onCreateView(String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        final View view = ThemeUtils.createView(name, context, attrs, mCurrentThemeColor);
        if (view instanceof ShapedImageView) {
            final ShapedImageView shapedImageView = (ShapedImageView) view;
            shapedImageView.setStyle(mProfileImageStyle);
        }
        if (view != null) return view;
        return super.onCreateView(name, context, attrs);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        final SpannableStringBuilder builder = new SpannableStringBuilder(title);
        final int themeResId = getCurrentThemeResourceId();
        final int themeColor = getThemeColor(), contrastColor = ColorUtils.getContrastYIQ(themeColor, 192);
        if (!ThemeUtils.isDarkTheme(themeResId)) {
            builder.setSpan(new ForegroundColorSpan(contrastColor), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        super.onTitleChanged(title, color);
    }

	public int getActionBarColor() {
		return ThemeUtils.getActionBarColor(this);
	}

	private void setTheme() {
		mCurrentThemeResource = getThemeResourceId();
		mCurrentThemeColor = getThemeColor();
		mCurrentActionBarColor = getActionBarColor();
        mCurrentThemeBackgroundAlpha = getThemeBackgroundAlpha();
		mProfileImageStyle = Utils.getProfileImageStyle(this);
        mCurrentThemeBackgroundOption = getThemeBackgroundOption();
		setTheme(mCurrentThemeResource);
        ThemeUtils.applyWindowBackground(this, getWindow(), mCurrentThemeResource, mCurrentThemeBackgroundOption, mCurrentThemeBackgroundAlpha);
	}
}