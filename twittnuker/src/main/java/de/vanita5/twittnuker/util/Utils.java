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

package de.vanita5.twittnuker.util;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.view.ActionProvider;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.MenuBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONException;
import de.vanita5.twittnuker.library.MicroBlogException;
import de.vanita5.twittnuker.library.twitter.model.RateLimitStatus;
import de.vanita5.twittnuker.fragment.AbsStatusesFragment;
import org.mariotaku.pickncrop.library.PNCUtils;
import org.mariotaku.sqliteqb.library.AllColumns;
import org.mariotaku.sqliteqb.library.Columns;
import org.mariotaku.sqliteqb.library.Columns.Column;
import org.mariotaku.sqliteqb.library.Expression;
import org.mariotaku.sqliteqb.library.Selectable;
import de.vanita5.twittnuker.Constants;
import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.annotation.CustomTabType;
import de.vanita5.twittnuker.extension.model.AccountDetailsExtensionsKt;
import de.vanita5.twittnuker.library.twitter.model.UrlEntity;
import de.vanita5.twittnuker.menu.FavoriteItemProvider;
import de.vanita5.twittnuker.model.AccountDetails;
import de.vanita5.twittnuker.model.AccountPreferences;
import de.vanita5.twittnuker.model.ParcelableStatus;
import de.vanita5.twittnuker.model.ParcelableUserMention;
import de.vanita5.twittnuker.model.PebbleMessage;
import de.vanita5.twittnuker.model.UserKey;
import de.vanita5.twittnuker.model.util.AccountUtils;
import de.vanita5.twittnuker.provider.TwidereDataStore.CachedUsers;
import de.vanita5.twittnuker.view.TabPagerIndicator;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.vanita5.twittnuker.util.TwidereLinkify.PATTERN_TWITTER_PROFILE_IMAGES;

public final class Utils implements Constants {

    public static final Pattern PATTERN_XML_RESOURCE_IDENTIFIER = Pattern.compile("res/xml/([\\w_]+)\\.xml");
    public static final Pattern PATTERN_RESOURCE_IDENTIFIER = Pattern.compile("@([\\w_]+)/([\\w_]+)");

    private static final UriMatcher HOME_TABS_URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {

        HOME_TABS_URI_MATCHER.addURI(CustomTabType.HOME_TIMELINE, null, TAB_CODE_HOME_TIMELINE);
        HOME_TABS_URI_MATCHER.addURI(CustomTabType.NOTIFICATIONS_TIMELINE, null, TAB_CODE_NOTIFICATIONS_TIMELINE);
        HOME_TABS_URI_MATCHER.addURI(CustomTabType.DIRECT_MESSAGES, null, TAB_CODE_DIRECT_MESSAGES);
    }


    private Utils() {
        throw new AssertionError("You are trying to create an instance for this utility class!");
    }

    public static void announceForAccessibilityCompat(final Context context, final View view, final CharSequence text,
                                                      final Class<?> cls) {
        final AccessibilityManager accessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (!accessibilityManager.isEnabled()) return;
        // Prior to SDK 16, announcements could only be made through FOCUSED
        // events. Jelly Bean (SDK 16) added support for speaking text verbatim
        // using the ANNOUNCEMENT event type.
        final int eventType;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            eventType = AccessibilityEvent.TYPE_VIEW_FOCUSED;
        } else {
            eventType = AccessibilityEventCompat.TYPE_ANNOUNCEMENT;
        }

        // Construct an accessibility event with the minimum recommended
        // attributes. An event without a class name or package may be dropped.
        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.getText().add(text);
        event.setClassName(cls.getName());
        event.setPackageName(context.getPackageName());
        event.setSource(view);

        // Sends the event directly through the accessibility manager. If your
        // application only targets SDK 14+, you should just call
        // getParent().requestSendAccessibilityEvent(this, event);
        accessibilityManager.sendAccessibilityEvent(event);
    }

    public static int calculateInSampleSize(final int width, final int height, final int preferredWidth,
                                            final int preferredHeight) {
        if (preferredHeight > height && preferredWidth > width) return 1;
        final int result = Math.round(Math.max(width, height) / (float) Math.max(preferredWidth, preferredHeight));
        return Math.max(1, result);
    }

    public static boolean closeSilently(final Closeable c) {
        if (c == null) return false;
        try {
            c.close();
        } catch (final IOException e) {
            return false;
        }
        return true;
    }

    public static int[] getAccountColors(@Nullable final AccountDetails[] accounts) {
        if (accounts == null) return null;
        final int[] colors = new int[accounts.length];
        for (int i = 0, j = accounts.length; i < j; i++) {
            colors[i] = accounts[i].color;
        }
        return colors;
    }

    public static boolean deleteMedia(final Context context, final Uri uri) {
        try {
            return PNCUtils.deleteMedia(context, uri);
        } catch (SecurityException e) {
            return false;
        }
    }

    public static String sanitizeMimeType(@Nullable final String contentType) {
        if (contentType == null) return null;
        switch (contentType) {
            case "image/jpg":
                return "image/jpeg";
        }
        return contentType;
    }

    public static class NoAccountException extends Exception {
        String accountHost;

        public String getAccountHost() {
            return accountHost;
        }

        public void setAccountHost(String accountHost) {
            this.accountHost = accountHost;
        }
    }

    public static String getUserKeyParam(Uri uri) {
        final String paramUserKey = uri.getQueryParameter(QUERY_PARAM_USER_KEY);
        if (paramUserKey == null) {
            return uri.getQueryParameter(QUERY_PARAM_USER_ID);
        }
        return paramUserKey;
    }

    public static Intent createStatusShareIntent(@NonNull final Context context, @NonNull final ParcelableStatus status) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, IntentUtils.INSTANCE.getStatusShareSubject(context, status));
        intent.putExtra(Intent.EXTRA_TEXT, IntentUtils.INSTANCE.getStatusShareText(context, status));
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    @Nullable
    public static UserKey[] getAccountKeys(@NonNull Context context, @Nullable Bundle args) {
        if (args == null) return null;
        if (args.containsKey(EXTRA_ACCOUNT_KEYS)) {
            return newParcelableArray(args.getParcelableArray(EXTRA_ACCOUNT_KEYS), UserKey.CREATOR);
        } else if (args.containsKey(EXTRA_ACCOUNT_KEY)) {
            final UserKey accountKey = args.getParcelable(EXTRA_ACCOUNT_KEY);
            if (accountKey == null) return new UserKey[0];
            return new UserKey[]{accountKey};
        } else if (args.containsKey(EXTRA_ACCOUNT_ID)) {
            final String accountId = String.valueOf(args.get(EXTRA_ACCOUNT_ID));
            try {
                if (Long.parseLong(accountId) <= 0) return null;
            } catch (NumberFormatException e) {
                // Ignore
            }
            final UserKey accountKey = DataStoreUtils.INSTANCE.findAccountKey(context, accountId);
            args.putParcelable(EXTRA_ACCOUNT_KEY, accountKey);
            if (accountKey == null) return new UserKey[]{new UserKey(accountId, null)};
            return new UserKey[]{accountKey};
        }
        return null;
    }

    @Nullable
    public static UserKey getAccountKey(@NonNull Context context, @Nullable Bundle args) {
        final UserKey[] accountKeys = getAccountKeys(context, args);
        if (accountKeys == null || accountKeys.length == 0) return null;
        return accountKeys[0];
    }

    @NonNull
    public static String getReadPositionTagWithAccount(@NonNull final String tag,
                                                       @Nullable final UserKey accountKey) {
        if (accountKey == null) return tag;
        return accountKey + ":" + tag;
    }

    @SuppressWarnings("deprecation")
    public static String formatSameDayTime(final Context context, final long timestamp) {
        if (context == null) return null;
        if (DateUtils.isToday(timestamp))
            return DateUtils.formatDateTime(context, timestamp,
                    DateFormat.is24HourFormat(context) ? DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR
                            : DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_12HOUR);
        return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE);
    }

    @SuppressWarnings("deprecation")
    public static String formatToLongTimeString(final Context context, final long timestamp) {
        if (context == null) return null;
        int formatFlags = DateUtils.FORMAT_NO_NOON_MIDNIGHT | DateUtils.FORMAT_ABBREV_ALL |
                DateUtils.FORMAT_CAP_AMPM | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME;
        return DateUtils.formatDateTime(context, timestamp, formatFlags);
    }

    public static int getAccountNotificationId(final int notificationType, final String accountId) {
        return Arrays.hashCode(new long[]{notificationType, Long.parseLong(accountId)});
    }

    public static boolean isComposeNowSupported(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN || context == null) return false;
        return hasNavBar(context);
    }

    public static boolean isOfficialCredentials(@NonNull final Context context, final UserKey accountKey) {
        final AccountDetails details = AccountUtils.getAccountDetails(AccountManager.get(context), accountKey, true);
        if (details == null) return false;
        return AccountDetailsExtensionsKt.isOfficial(details, context);
    }


    public static boolean isOfficialCredentials(@NonNull final Context context,
                                                @NonNull final AccountDetails account) {
        return AccountDetailsExtensionsKt.isOfficial(account, context);
    }

    public static boolean setLastSeen(Context context, ParcelableUserMention[] entities, long time) {
        if (entities == null) return false;
        boolean result = false;
        for (ParcelableUserMention entity : entities) {
            result |= setLastSeen(context, entity.key, time);
        }
        return result;
    }

    public static boolean setLastSeen(Context context, UserKey userId, long time) {
        final ContentResolver cr = context.getContentResolver();
        final ContentValues values = new ContentValues();
        if (time > 0) {
            values.put(CachedUsers.LAST_SEEN, time);
        } else {
            // Zero or negative value means remove last seen
            values.putNull(CachedUsers.LAST_SEEN);
        }
        final String where = Expression.equalsArgs(CachedUsers.USER_KEY).getSQL();
        final String[] selectionArgs = {userId.toString()};
        return cr.update(CachedUsers.CONTENT_URI, values, where, selectionArgs) > 0;
    }


    public static Selectable getColumnsFromProjection(final String... projection) {
        if (projection == null) return new AllColumns();
        final int length = projection.length;
        final Column[] columns = new Column[length];
        for (int i = 0; i < length; i++) {
            columns[i] = new Column(projection[i]);
        }
        return new Columns(columns);
    }

    @Nullable
    public static UserKey getDefaultAccountKey(final Context context) {
        if (context == null) return null;
        final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        final String string = prefs.getString(KEY_DEFAULT_ACCOUNT_KEY, null);
        UserKey accountKey = string != null ? UserKey.valueOf(string) : null;
        final UserKey[] accountKeys = DataStoreUtils.INSTANCE.getAccountKeys(context);
        int idMatchIdx = -1;
        for (int i = 0, accountIdsLength = accountKeys.length; i < accountIdsLength; i++) {
            if (accountKeys[i].equals(accountKey)) {
                idMatchIdx = i;
            }
        }
        if (idMatchIdx != -1) {
            return accountKeys[idMatchIdx];
        }
        if (accountKeys.length > 0 && !ArrayUtils.contains(accountKeys, accountKey)) {
             /* TODO: this is just a quick fix */
            return accountKeys[0];
        }
        return null;
    }

    public static String getErrorMessage(final Context context, final CharSequence message) {
        if (context == null) return ParseUtils.parseString(message);
        if (TextUtils.isEmpty(message)) return context.getString(R.string.error_unknown_error);
        return context.getString(R.string.error_message, message);
    }

    public static String getErrorMessage(final Context context, final CharSequence action, final CharSequence message) {
        if (context == null || TextUtils.isEmpty(action)) return ParseUtils.parseString(message);
        if (TextUtils.isEmpty(message)) return context.getString(R.string.error_unknown_error);
        return context.getString(R.string.error_message_with_action, action, message);
    }

    public static String getErrorMessage(final Context context, final CharSequence action, @Nullable final Throwable t) {
        if (context == null) return null;
        if (t instanceof MicroBlogException)
            return getTwitterErrorMessage(context, action, (MicroBlogException) t);
        else if (t != null) return getErrorMessage(context, trimLineBreak(t.getMessage()));
        Analyzer.Companion.logException(new IllegalStateException());
        return context.getString(R.string.error_unknown_error);
    }

    @Nullable
    public static String getErrorMessage(@NonNull final Context context, final Throwable t) {
        if (t == null) {
            Analyzer.Companion.logException(new IllegalStateException());
            return context.getString(R.string.error_unknown_error);
        }
        if (t instanceof MicroBlogException)
            return StatusCodeMessageUtils.getMicroBlogErrorMessage(context, (MicroBlogException) t);
        return t.getMessage();
    }


    public static String getMediaUploadStatus(@NonNull final Context context,
                                              @Nullable final CharSequence[] links,
                                              @Nullable final CharSequence text) {
        if (ArrayUtils.isEmpty(links) || text == null) return ParseUtils.parseString(text);
        return text + " " + TwidereArrayUtils.toString(links, ' ', false);
    }

    public static File getInternalCacheDir(final Context context, final String cacheDirName) {
        if (context == null) throw new NullPointerException();
        final File cacheDir = new File(context.getCacheDir(), cacheDirName);
        if (cacheDir.isDirectory() || cacheDir.mkdirs()) return cacheDir;
        return new File(context.getCacheDir(), cacheDirName);
    }

    @Nullable
    public static File getExternalCacheDir(final Context context, final String cacheDirName,
            final long sizeInBytes) {
        if (context == null) throw new NullPointerException();
        final File externalCacheDir = context.getExternalCacheDir();
        if (externalCacheDir == null) return null;
        final File cacheDir = new File(externalCacheDir, cacheDirName);
        if (sizeInBytes > 0 && externalCacheDir.getFreeSpace() < sizeInBytes / 10) {
            // Less then 10% space available
            return null;
        }
        if (cacheDir.isDirectory() || cacheDir.mkdirs()) return cacheDir;
        return null;
    }

    public static String getLocalizedNumber(final Locale locale, final Number number) {
        final NumberFormat nf = NumberFormat.getInstance(locale);
        return nf.format(number);
    }

    public static String getOriginalTwitterProfileImage(final String url) {
        if (url == null) return null;
        final Matcher matcher = PATTERN_TWITTER_PROFILE_IMAGES.matcher(url);
        if (matcher.matches())
            return matcher.replaceFirst("$1$2/profile_images/$3/$4$6");
        return url;
    }

    public static String getQuoteStatus(final Context context, final ParcelableStatus status) {
        if (context == null) return null;
        String quoteFormat = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).getString(
                KEY_QUOTE_FORMAT, DEFAULT_QUOTE_FORMAT);
        if (TextUtils.isEmpty(quoteFormat)) {
            quoteFormat = DEFAULT_QUOTE_FORMAT;
        }
        String result = quoteFormat.replace(FORMAT_PATTERN_LINK, LinkCreator.getStatusWebLink(status).toString());
        result = result.replace(FORMAT_PATTERN_NAME, status.user_screen_name);
        result = result.replace(FORMAT_PATTERN_TEXT, status.text_plain);
        return result;
    }

    public static int getResId(final Context context, final String string) {
        if (context == null || string == null) return 0;
        Matcher m = PATTERN_RESOURCE_IDENTIFIER.matcher(string);
        final Resources res = context.getResources();
        if (m.matches()) return res.getIdentifier(m.group(2), m.group(1), context.getPackageName());
        m = PATTERN_XML_RESOURCE_IDENTIFIER.matcher(string);
        if (m.matches()) return res.getIdentifier(m.group(1), "xml", context.getPackageName());
        return 0;
    }


    public static String getShareStatus(final Context context, final CharSequence title, final CharSequence text) {
        if (context == null) return null;
        String shareFormat = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).getString(
                KEY_SHARE_FORMAT, DEFAULT_SHARE_FORMAT);
        if (TextUtils.isEmpty(shareFormat)) {
            shareFormat = DEFAULT_SHARE_FORMAT;
        }
        if (TextUtils.isEmpty(title)) return ParseUtils.parseString(text);
        return shareFormat.replace(FORMAT_PATTERN_TITLE, title).replace(FORMAT_PATTERN_TEXT, text != null ? text : "");
    }

    public static String getTabDisplayOption(final Context context) {
        if (context == null) return null;
        final String defaultOption = context.getString(R.string.default_tab_display_option);
        final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TAB_DISPLAY_OPTION, defaultOption);
    }

    public static int getTabDisplayOptionInt(final Context context) {
        return getTabDisplayOptionInt(getTabDisplayOption(context));
    }

    public static int getTabDisplayOptionInt(final String option) {
        if (VALUE_TAB_DISPLAY_OPTION_ICON.equals(option))
            return TabPagerIndicator.DisplayOption.ICON;
        else if (VALUE_TAB_DISPLAY_OPTION_LABEL.equals(option))
            return TabPagerIndicator.DisplayOption.LABEL;
        return TabPagerIndicator.DisplayOption.BOTH;
    }

    public static boolean hasNavBar(@NonNull Context context) {
        final Resources resources = context.getResources();
        if (resources == null) return false;
        int id = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        if (id > 0) {
            return resources.getBoolean(id);
        } else {
            // Check for keys
            return !KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
                    && !KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME);
        }
    }

    public static String getTwitterErrorMessage(final Context context, final CharSequence action,
                                                final MicroBlogException te) {
        if (context == null) return null;
        if (te == null) return context.getString(R.string.error_unknown_error);
        if (te.exceededRateLimitation()) {
            final RateLimitStatus status = te.getRateLimitStatus();
            final long secUntilReset = status.getSecondsUntilReset() * 1000;
            final String nextResetTime = ParseUtils.parseString(DateUtils.getRelativeTimeSpanString(System.currentTimeMillis()
                    + secUntilReset));
            if (TextUtils.isEmpty(action))
                return context.getString(R.string.error_message_rate_limit, nextResetTime.trim());
            return context.getString(R.string.error_message_rate_limit_with_action, action, nextResetTime.trim());
        } else if (te.getErrorCode() > 0) {
            final String msg = StatusCodeMessageUtils.getTwitterErrorMessage(context, te.getErrorCode());
            return getErrorMessage(context, action, msg != null ? msg : trimLineBreak(te.getMessage()));
        } else if (te.getCause() instanceof IOException)
            return getErrorMessage(context, action, context.getString(R.string.message_toast_network_error));
        else if (te.getCause() instanceof JSONException)
            return getErrorMessage(context, action, context.getString(R.string.message_api_data_corrupted));
        else
            return getErrorMessage(context, action, trimLineBreak(te.getMessage()));
    }

    @Nullable
    public static String getTwitterErrorMessage(final Context context, final MicroBlogException te) {
        if (te == null) return null;
        if (StatusCodeMessageUtils.containsTwitterError(te.getErrorCode())) {
            return StatusCodeMessageUtils.getTwitterErrorMessage(context, te.getErrorCode());
        } else if (StatusCodeMessageUtils.containsHttpStatus(te.getStatusCode())) {
            return StatusCodeMessageUtils.getHttpStatusMessage(context, te.getStatusCode());
        } else if (te.getErrorMessage() != null) {
            return te.getErrorMessage();
        }
        return te.getMessage();
    }


    public static String getTwitterProfileImageOfSize(@NonNull final String url, @NonNull final String size) {
        final Matcher matcher = PATTERN_TWITTER_PROFILE_IMAGES.matcher(url);
        if (matcher.matches()) {
            return matcher.replaceFirst("$1$2/profile_images/$3/$4_" + size + "$6");
        }
        return url;
    }

    @DrawableRes
    public static int getUserTypeIconRes(final boolean isVerified, final boolean isProtected) {
        if (isVerified)
            return R.drawable.ic_user_type_verified;
        else if (isProtected) return R.drawable.ic_user_type_protected;
        return 0;
    }

    @StringRes
    public static int getUserTypeDescriptionRes(final boolean isVerified, final boolean isProtected) {
        if (isVerified)
            return R.string.user_type_verified;
        else if (isProtected) return R.string.user_type_protected;
        return 0;
    }

    public static boolean isBatteryOkay(final Context context) {
        if (context == null) return false;
        final Context app = context.getApplicationContext();
        final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Intent intent;
        try {
            intent = app.registerReceiver(null, filter);
        } catch (Exception e) {
            return false;
        }
        if (intent == null) return false;
        final boolean plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        final float level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        final float scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return plugged || level / scale > 0.15f;
    }

    public static boolean isMyAccount(@NonNull final Context context, @NonNull final UserKey accountKey) {
        final AccountManager am = AccountManager.get(context);
        return AccountUtils.findByAccountKey(am, accountKey) != null;
    }

    public static boolean isMyAccount(@NonNull final Context context, @NonNull final String screenName) {
        final AccountManager am = AccountManager.get(context);
        return AccountUtils.findByScreenName(am, screenName) != null;
    }

    public static boolean isMyRetweet(final ParcelableStatus status) {
        return status != null && isMyRetweet(status.account_key, status.retweeted_by_user_key,
                status.my_retweet_id);
    }

    public static boolean isMyRetweet(final UserKey accountId, final UserKey retweetedById, final String myRetweetId) {
        return accountId.equals(retweetedById) || myRetweetId != null;
    }

    public static int matchTabCode(@Nullable final Uri uri) {
        if (uri == null) return UriMatcher.NO_MATCH;
        return HOME_TABS_URI_MATCHER.match(uri);
    }


    @CustomTabType
    public static String matchTabType(@Nullable final Uri uri) {
        return getTabType(matchTabCode(uri));
    }

    @CustomTabType
    public static String getTabType(final int code) {
        switch (code) {
            case TAB_CODE_HOME_TIMELINE: {
                return CustomTabType.HOME_TIMELINE;
            }
            case TAB_CODE_NOTIFICATIONS_TIMELINE: {
                return CustomTabType.NOTIFICATIONS_TIMELINE;
            }
            case TAB_CODE_DIRECT_MESSAGES: {
                return CustomTabType.DIRECT_MESSAGES;
            }
        }
        return null;
    }


    @SuppressWarnings("SuspiciousSystemArraycopy")
    public static <T extends Parcelable> T[] newParcelableArray(Parcelable[] array, Parcelable.Creator<T> creator) {
        if (array == null) return null;
        final T[] result = creator.newArray(array.length);
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    public static boolean setNdefPushMessageCallback(Activity activity, CreateNdefMessageCallback callback) {
        try {
            final NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);
            if (adapter == null) return false;
            adapter.setNdefPushMessageCallback(callback, activity);
            return true;
        } catch (SecurityException e) {
            Log.w(LOGTAG, e);
        }
        return false;
    }

    public static int getInsetsTopWithoutActionBarHeight(Context context, int top) {
        final int actionBarHeight;
        if (context instanceof AppCompatActivity) {
            actionBarHeight = getActionBarHeight(((AppCompatActivity) context).getSupportActionBar());
        } else if (context instanceof Activity) {
            actionBarHeight = getActionBarHeight(((Activity) context).getActionBar());
        } else {
            return top;
        }
        if (actionBarHeight > top) {
            return top;
        }
        return top - actionBarHeight;
    }

    public static void restartActivity(final Activity activity) {
        if (activity == null) return;
        final int enterAnim = android.R.anim.fade_in;
        final int exitAnim = android.R.anim.fade_out;
        activity.finish();
        activity.overridePendingTransition(enterAnim, exitAnim);
        activity.startActivity(activity.getIntent());
        activity.overridePendingTransition(enterAnim, exitAnim);
    }

    static boolean isMyStatus(ParcelableStatus status) {
        if (isMyRetweet(status)) return true;
        return status.account_key.maybeEquals(status.user_key);
    }

    public static void showErrorMessage(final Context context, final CharSequence message, final boolean longMessage) {
        if (context == null) return;
        final Toast toast = Toast.makeText(context, message, longMessage ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        toast.show();
    }

    public static void showErrorMessage(final Context context, final CharSequence action,
                                        final CharSequence message, final boolean longMessage) {
        if (context == null) return;
        showErrorMessage(context, getErrorMessage(context, action, message), longMessage);
    }

    public static void showErrorMessage(final Context context, final CharSequence action,
                                        @Nullable final Throwable t, final boolean longMessage) {
        if (context == null) return;
        if (t instanceof MicroBlogException) {
            showTwitterErrorMessage(context, action, (MicroBlogException) t, longMessage);
            return;
        }
        showErrorMessage(context, getErrorMessage(context, action, t), longMessage);
    }

    public static void showErrorMessage(final Context context, final int actionRes, final String desc,
                                        final boolean longMessage) {
        if (context == null) return;
        showErrorMessage(context, context.getString(actionRes), desc, longMessage);
    }

    public static void showErrorMessage(final Context context, final int action,
                                        @Nullable final Throwable t,
                                        final boolean long_message) {
        if (context == null) return;
        showErrorMessage(context, context.getString(action), t, long_message);
    }

    public static void showInfoMessage(final Context context, final CharSequence message, final boolean long_message) {
        if (context == null || TextUtils.isEmpty(message)) return;
        final Toast toast = Toast.makeText(context, message, long_message ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        toast.show();
    }

    public static void showInfoMessage(final Context context, final int resId, final boolean long_message) {
        if (context == null) return;
        showInfoMessage(context, context.getText(resId), long_message);
    }

    public static void showMenuItemToast(final View v, final CharSequence text, final boolean isBottomBar) {
        final int[] screenPos = new int[2];
        final Rect displayFrame = new Rect();
        v.getLocationOnScreen(screenPos);
        v.getWindowVisibleDisplayFrame(displayFrame);
        final int width = v.getWidth();
        final int height = v.getHeight();
        final int screenWidth = v.getResources().getDisplayMetrics().widthPixels;
        final Toast cheatSheet = Toast.makeText(v.getContext().getApplicationContext(), text, Toast.LENGTH_SHORT);
        if (isBottomBar) {
            // Show along the bottom center
            cheatSheet.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, height);
        } else {
            // Show along the top; follow action buttons
            cheatSheet.setGravity(Gravity.TOP | GravityCompat.END, screenWidth - screenPos[0] - width / 2, height);
        }
        cheatSheet.show();
    }

    public static void showOkMessage(final Context context, final CharSequence message, final boolean longMessage) {
        if (context == null || TextUtils.isEmpty(message)) return;
        final Toast toast = Toast.makeText(context, message, longMessage ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        toast.show();
    }

    public static void showOkMessage(final Context context, final int resId, final boolean long_message) {
        if (context == null) return;
        showOkMessage(context, context.getText(resId), long_message);
    }

    public static void showTwitterErrorMessage(final Context context, final CharSequence action,
                                               final MicroBlogException te, final boolean long_message) {
        if (context == null) return;
        final String message;
        if (te != null) {
            if (action != null) {
                final RateLimitStatus status = te.getRateLimitStatus();
                if (te.exceededRateLimitation() && status != null) {
                    final long secUntilReset = status.getSecondsUntilReset() * 1000;
                    final String nextResetTime = ParseUtils.parseString(DateUtils.getRelativeTimeSpanString(System
                            .currentTimeMillis() + secUntilReset));
                    message = context.getString(R.string.error_message_rate_limit_with_action, action,
                            nextResetTime.trim());
                } else if (isErrorCodeMessageSupported(te)) {
                    final String msg = StatusCodeMessageUtils
                            .getMessage(context, te.getStatusCode(), te.getErrorCode());
                    message = context.getString(R.string.error_message_with_action, action, msg != null ? msg
                            : trimLineBreak(te.getMessage()));
                } else if (!TextUtils.isEmpty(te.getErrorMessage())) {
                    message = context.getString(R.string.error_message_with_action, action,
                            trimLineBreak(te.getErrorMessage()));
                } else if (te.getCause() instanceof IOException) {
                    message = context.getString(R.string.error_message_with_action, action,
                            context.getString(R.string.message_toast_network_error));
                } else {
                    message = context.getString(R.string.error_message_with_action, action,
                            trimLineBreak(te.getMessage()));
                }
            } else {
                message = context.getString(R.string.error_message, trimLineBreak(te.getMessage()));
            }
        } else {
            message = context.getString(R.string.error_unknown_error);
        }
        showErrorMessage(context, message, long_message);
    }

    public static void startStatusShareChooser(final Context context, final ParcelableStatus status) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        final String name = status.user_name, screenName = status.user_screen_name;
        final String timeString = formatToLongTimeString(context, status.timestamp);
        final String subject = context.getString(R.string.status_share_subject_format_with_time, name, screenName, timeString);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, status.text_plain);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)));
    }

    public static String trimLineBreak(final String orig) {
        if (orig == null) return null;
        return orig.replaceAll("\\n+", "\n");
    }

    private static Drawable getMetadataDrawable(final PackageManager pm, final ActivityInfo info, final String key) {
        if (pm == null || info == null || info.metaData == null || key == null || !info.metaData.containsKey(key))
            return null;
        return pm.getDrawable(info.packageName, info.metaData.getInt(key), info.applicationInfo);
    }

    private static boolean isErrorCodeMessageSupported(final MicroBlogException te) {
        if (te == null) return false;
        return StatusCodeMessageUtils.containsHttpStatus(te.getStatusCode())
                || StatusCodeMessageUtils.containsTwitterError(te.getErrorCode());
    }

    public static int getActionBarHeight(@Nullable ActionBar actionBar) {
        if (actionBar == null) return 0;
        final Context context = actionBar.getThemedContext();
        final TypedValue tv = new TypedValue();
        final int height = actionBar.getHeight();
        if (height > 0) return height;
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            return TypedValue.complexToDimensionPixelSize(tv.data, context.getResources().getDisplayMetrics());
        }
        return 0;
    }

    public static String parseURLEntities(String text, final UrlEntity[] entities) {
        for (UrlEntity entity : entities) {
            final int start = entity.getStart(), end = entity.getEnd();
            final String displayUrl = entity.getDisplayUrl();
            if (displayUrl != null && !displayUrl.isEmpty() && start >= 0 && end >= 0) {
                StringBuffer bf = new StringBuffer(text);
                return bf.replace(start, end, displayUrl).toString();
            }
        }
        return text;
    }

    public static void retweet(ParcelableStatus status, AsyncTwitterWrapper twitter) {
        if (isMyRetweet(status)) {
            twitter.cancelRetweetAsync(status.account_key, status.id, status.my_retweet_id);
        } else {
            twitter.retweetStatusAsync(status.account_key, status);
        }
    }

    public static void favorite(ParcelableStatus status, AsyncTwitterWrapper twitter, MenuItem item) {
        if (status.is_favorite) {
            twitter.destroyFavoriteAsync(status.account_key, status.id);
        } else {
            ActionProvider provider = MenuItemCompat.getActionProvider(item);
            if (provider instanceof FavoriteItemProvider) {
                ((FavoriteItemProvider) provider).invokeItem(item,
                        new AbsStatusesFragment.DefaultOnLikedListener(twitter, status, null));
            } else {
                twitter.createFavoriteAsync(status.account_key, status);
            }
        }
    }

    public static int getActionBarHeight(@Nullable android.support.v7.app.ActionBar actionBar) {
        if (actionBar == null) return 0;
        final Context context = actionBar.getThemedContext();
        final TypedValue tv = new TypedValue();
        final int height = actionBar.getHeight();
        if (height > 0) return height;
        if (context.getTheme().resolveAttribute(R.attr.actionBarSize, tv, true)) {
            return TypedValue.complexToDimensionPixelSize(tv.data, context.getResources().getDisplayMetrics());
        }
        return 0;
    }


    public static <T> Object findFieldOfTypes(T obj, Class<? extends T> cls, Class<?>... checkTypes) {
        labelField:
        for (Field field : cls.getDeclaredFields()) {
            field.setAccessible(true);
            final Object fieldObj;
            try {
                fieldObj = field.get(obj);
            } catch (Exception ignore) {
                continue;
            }
            if (fieldObj != null) {
                final Class<?> type = fieldObj.getClass();
                for (Class<?> checkType : checkTypes) {
                    if (!checkType.isAssignableFrom(type)) continue labelField;
                }
                return fieldObj;
            }
        }
        return null;
    }

    public static int getNotificationId(int baseId, @Nullable UserKey accountId) {
        int result = baseId;
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        return result;
    }

    @SuppressLint("InlinedApi")
    public static boolean isCharging(final Context context) {
        if (context == null) return false;
        final Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return false;
        final int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC
                || plugged == BatteryManager.BATTERY_PLUGGED_USB
                || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    public static boolean isMediaPreviewEnabled(Context context, SharedPreferencesWrapper preferences) {
        if (!preferences.getBoolean(KEY_MEDIA_PREVIEW)) return false;
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null
                && !(networkInfo.getType() == ConnectivityManager.TYPE_MOBILE && preferences.getBoolean(KEY_BANDWIDTH_SAVING_MODE));
    }

    /**
     * Send Notifications to Pebble smartwatches
     *
     * @param context Context
     * @param title String
     * @param message String
     */
    public static void sendPebbleNotification(@NonNull final Context context, @Nullable final String title, @NonNull final String message) {
        String appName;

        if (title == null) {
            appName = context.getString(R.string.app_name);
        } else {
            appName = context.getString(R.string.app_name) + " - " + title;
        }

        if (TextUtils.isEmpty(message)) return;
        final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);

        if (prefs.getBoolean(KEY_PEBBLE_NOTIFICATIONS, false)) {

            final List<PebbleMessage> messages = new ArrayList<>();
            messages.add(new PebbleMessage(appName, message));

            final Intent intent = new Intent(INTENT_ACTION_PEBBLE_NOTIFICATION);
            intent.putExtra("messageType", "PEBBLE_ALERT");
            intent.putExtra("sender", appName);
            intent.putExtra("notificationData", JsonSerializer.serialize(messages, PebbleMessage.class));

            context.getApplicationContext().sendBroadcast(intent);
        }

    }

    @Nullable
    public static Location getCachedLocation(Context context) {
        DebugLog.v(LOGTAG, "Fetching cached location", new Exception());
        Location location = null;
        final LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        try {
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (SecurityException ignore) {

        }
        if (location != null) return location;
        try {
            location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException ignore) {

        }
        return location;
    }

    public static boolean checkPlayServices(final Activity activity) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(activity);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(activity, resultCode, 9000)
                        .show();
            } else {
                Log.i("PlayServices", "This device is not supported.");
            }
            return false;
        }
        return true;
    }

    public static boolean checkDeviceCompatible() {
        try {
            Menu.class.isAssignableFrom(MenuBuilder.class);
        } catch (Error e) {
            Analyzer.Companion.logException(e);
            return false;
        }
        return true;
    }

    /**
     * Detect whether screen minimum width is not smaller than 600dp, regardless split screen mode
     */
    public static boolean isDeviceTablet(@NonNull Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Display defaultDisplay = wm.getDefaultDisplay();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            defaultDisplay.getMetrics(metrics);
        } else {
            defaultDisplay.getRealMetrics(metrics);
        }
        final float mw = Math.min(metrics.widthPixels / metrics.density, metrics.heightPixels / metrics.density);
        return mw >= 600;
    }

    /*
     * May return false on tablets when using split window
     */
    public static boolean isScreenTablet(@NonNull Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.widthPixels / metrics.density >= 600;
    }

}