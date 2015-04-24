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

package de.vanita5.twittnuker.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;
import android.widget.Toast;

import com.nostra13.universalimageloader.utils.IoUtils;
import com.twitter.Extractor;

import org.mariotaku.querybuilder.Expression;
import de.vanita5.twittnuker.Constants;
import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.app.TwittnukerApplication;
import de.vanita5.twittnuker.model.ParcelableAccount;
import de.vanita5.twittnuker.model.ParcelableDirectMessage;
import de.vanita5.twittnuker.model.ParcelableLocation;
import de.vanita5.twittnuker.model.ParcelableMediaUpdate;
import de.vanita5.twittnuker.model.ParcelableStatus;
import de.vanita5.twittnuker.model.ParcelableStatusUpdate;
import de.vanita5.twittnuker.model.SingleResponse;
import de.vanita5.twittnuker.preference.ServicePickerPreference;
import de.vanita5.twittnuker.provider.TwidereDataStore.CachedHashtags;
import de.vanita5.twittnuker.provider.TwidereDataStore.DirectMessages;
import de.vanita5.twittnuker.provider.TwidereDataStore.Drafts;
import de.vanita5.twittnuker.util.AsyncTwitterWrapper;
import de.vanita5.twittnuker.util.BitmapUtils;
import de.vanita5.twittnuker.util.ContentValuesCreator;
import de.vanita5.twittnuker.util.ListUtils;
import de.vanita5.twittnuker.util.ParseUtils;
import de.vanita5.twittnuker.util.StatusCodeMessageUtils;
import de.vanita5.twittnuker.util.TwidereValidator;
import de.vanita5.twittnuker.util.Utils;
import de.vanita5.twittnuker.util.io.ContentLengthInputStream;
import de.vanita5.twittnuker.util.io.ContentLengthInputStream.ReadListener;
import de.vanita5.twittnuker.util.shortener.TweetShortenerUtils;
import de.vanita5.twittnuker.util.shortener.TweetShortenerUtils.ShortenedStatusModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import twitter4j.MediaUploadResponse;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.conf.Configuration;
import twitter4j.media.ImageUpload;
import twitter4j.media.ImageUploadFactory;
import twitter4j.media.MediaProvider;

import static android.text.TextUtils.isEmpty;
import static de.vanita5.twittnuker.util.ContentValuesCreator.createMessageDraft;
import static de.vanita5.twittnuker.util.Utils.getImagePathFromUri;
import static de.vanita5.twittnuker.util.Utils.getImageUploadStatus;
import static de.vanita5.twittnuker.util.Utils.getTwitterInstance;

public class BackgroundOperationService extends IntentService implements Constants {

	private TwidereValidator mValidator;
	private final Extractor extractor = new Extractor();

	private Handler mHandler;
	private SharedPreferences mPreferences;
	private ContentResolver mResolver;
	private NotificationManager mNotificationManager;
	private AsyncTwitterWrapper mTwitter;

	private String mUploader;
	private String mShortener;

	private boolean mUseUploader, mUseShortener;

	public BackgroundOperationService() {
		super("background_operation");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		final TwittnukerApplication app = TwittnukerApplication.getInstance(this);
		mHandler = new Handler();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
		mValidator = new TwidereValidator(this);
		mResolver = getContentResolver();
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mTwitter = app.getTwitterWrapper();
		final String uploaderComponent = mPreferences.getString(KEY_MEDIA_UPLOADER, null);
		final String shortenerComponent = mPreferences.getString(KEY_STATUS_SHORTENER, null);
        mUseUploader = !ServicePickerPreference.isNoneValue(uploaderComponent);
		mUseShortener = !ServicePickerPreference.isNoneValue(shortenerComponent);
		mUploader = mUseUploader ? uploaderComponent : null;
		mShortener = mUseShortener ? shortenerComponent : null;
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId) {
		super.onStartCommand(intent, flags, startId);
		return START_STICKY;
	}

    public void showErrorMessage(final CharSequence message, final boolean longMessage) {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				Utils.showErrorMessage(BackgroundOperationService.this, message, longMessage);
			}
		});
	}

    public void showErrorMessage(final int actionRes, final Exception e, final boolean longMessage) {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
                Utils.showErrorMessage(BackgroundOperationService.this, actionRes, e, longMessage);
			}
		});
	}

    public void showErrorMessage(final int actionRes, final String message, final boolean longMessage) {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
                Utils.showErrorMessage(BackgroundOperationService.this, actionRes, message, longMessage);
			}
		});
	}

    public void showOkMessage(final int messageRes, final boolean longMessage) {
        showToast(getString(messageRes), longMessage);
    }

    private void showToast(final CharSequence message, final boolean longMessage) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
                Toast.makeText(BackgroundOperationService.this, message, longMessage ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	protected void onHandleIntent(final Intent intent) {
		if (intent == null) return;
		final String action = intent.getAction();

        switch (action) {
            case INTENT_ACTION_UPDATE_STATUS:
			handleUpdateStatusIntent(intent);
                break;
            case INTENT_ACTION_SEND_DIRECT_MESSAGE:
			handleSendDirectMessageIntent(intent);
                break;
            case INTENT_ACTION_DISCARD_DRAFT:
            handleDiscardDraftIntent(intent);
                break;
		}
	}

    private void handleDiscardDraftIntent(Intent intent) {
        final Uri data = intent.getData();
        if (data == null) return;
        mNotificationManager.cancel(data.toString(), NOTIFICATION_ID_DRAFTS);
        final ContentResolver contentResolver = getContentResolver();
        final long id = ParseUtils.parseLong(data.getLastPathSegment(), -1);
        final Expression where = Expression.equals(Drafts._ID, id);
        contentResolver.delete(Drafts.CONTENT_URI, where.getSQL(), null);
    }

	private Notification buildNotification(final String title, final String message, final int icon,
			final Intent content_intent, final Intent deleteIntent, boolean showProgress) {
		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setTicker(message);
		builder.setContentTitle(title);
		builder.setContentText(message);
		builder.setAutoCancel(true);
		builder.setWhen(System.currentTimeMillis());
		builder.setSmallIcon(icon);
		if(showProgress) builder.setProgress(0, 0, true);
		if (deleteIntent != null) {
            builder.setDeleteIntent(PendingIntent.getBroadcast(this, 0, deleteIntent,
					PendingIntent.FLAG_UPDATE_CURRENT));
		}
		if (content_intent != null) {
			content_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			builder.setContentIntent(PendingIntent.getActivity(this, 0, content_intent,
					PendingIntent.FLAG_UPDATE_CURRENT));
		}
		// final Uri defRingtone =
		// RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
		// final String path =
		// mPreferences.getString(KEY_NOTIFICATION_RINGTONE, "");
		// builder.setSound(isEmpty(path) ? defRingtone : Uri.parse(path),
		// Notification.STREAM_DEFAULT);
		// builder.setLights(HOLO_BLUE_LIGHT, 1000, 2000);
		// builder.setDefaults(Notification.DEFAULT_VIBRATE);
		return builder.build();
	}

	private void handleSendDirectMessageIntent(final Intent intent) {
		final long accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1);
		final long recipientId = intent.getLongExtra(EXTRA_RECIPIENT_ID, -1);
		final String imageUri = intent.getStringExtra(EXTRA_IMAGE_URI);
		final String text = intent.getStringExtra(EXTRA_TEXT);
		if (accountId <= 0 || recipientId <= 0 || isEmpty(text)) return;
		final String title = getString(R.string.sending_direct_message);
		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setSmallIcon(R.drawable.ic_stat_send);
		builder.setProgress(100, 0, true);
		builder.setTicker(title);
		builder.setContentTitle(title);
		builder.setContentText(text);
        builder.setOngoing(true);
		final Notification notification = builder.build();
		startForeground(NOTIFICATION_ID_SEND_DIRECT_MESSAGE, notification);
		final SingleResponse<ParcelableDirectMessage> result = sendDirectMessage(builder, accountId, recipientId, text,
				imageUri);

		if (result.getData() != null && result.getData().id > 0) {
            final ContentValues values = ContentValuesCreator.createDirectMessage(result.getData());
			final String delete_where = DirectMessages.ACCOUNT_ID + " = " + accountId + " AND "
					+ DirectMessages.MESSAGE_ID + " = " + result.getData().id;
			mResolver.delete(DirectMessages.Outbox.CONTENT_URI, delete_where, null);
			mResolver.insert(DirectMessages.Outbox.CONTENT_URI, values);
			showOkMessage(R.string.direct_message_sent, false);


		} else {
            final ContentValues values = createMessageDraft(accountId, recipientId, text, imageUri);
			mResolver.insert(Drafts.CONTENT_URI, values);
			showErrorMessage(R.string.action_sending_direct_message, result.getException(), true);
		}
        stopForeground(false);
        mNotificationManager.cancel(NOTIFICATION_ID_SEND_DIRECT_MESSAGE);
	}

	private void handleUpdateStatusIntent(final Intent intent) {
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		final ParcelableStatusUpdate status = intent.getParcelableExtra(EXTRA_STATUS);
		final Parcelable[] status_parcelables = intent.getParcelableArrayExtra(EXTRA_STATUSES);
		final ParcelableStatusUpdate[] statuses;
		if (status_parcelables != null) {
			statuses = new ParcelableStatusUpdate[status_parcelables.length];
			for (int i = 0, j = status_parcelables.length; i < j; i++) {
				statuses[i] = (ParcelableStatusUpdate) status_parcelables[i];
			}
		} else if (status != null) {
			statuses = new ParcelableStatusUpdate[1];
			statuses[0] = status;
		} else
			return;
        startForeground(NOTIFICATION_ID_UPDATE_STATUS, updateUpdateStatusNotification(this, builder, 0, null));
		for (final ParcelableStatusUpdate item : statuses) {
            mNotificationManager.notify(NOTIFICATION_ID_UPDATE_STATUS,
                    updateUpdateStatusNotification(this, builder, 0, item));
            final ContentValues draftValues = ContentValuesCreator.createStatusDraft(item,
                    ParcelableAccount.getAccountIds(item.accounts));
            final Uri draftUri = mResolver.insert(Drafts.CONTENT_URI, draftValues);
            final long draftId = ParseUtils.parseLong(draftUri.getLastPathSegment(), -1);
            mTwitter.addSendingDraftId(draftId);
            final List<SingleResponse<ParcelableStatus>> result = updateStatus(builder, item);
			boolean failed = false;
			Exception exception = null;
            final Expression where = Expression.equals(Drafts._ID, draftId);
            final List<Long> failedAccountIds = ListUtils.fromArray(ParcelableAccount.getAccountIds(item.accounts));

			for (final SingleResponse<ParcelableStatus> response : result) {

				if (response.getData() == null) {
					failed = true;
					if (exception == null) {
						exception = response.getException();
					}
				} else if (response.getData().account_id > 0) {
                    failedAccountIds.remove(response.getData().account_id);
				}
			}

			if (result.isEmpty()) {
				showErrorMessage(R.string.action_updating_status, getString(R.string.no_account_selected), false);
			} else if (failed) {
				// If the status is a duplicate, there's no need to save it to
				// drafts.
				if (exception instanceof TwitterException
						&& ((TwitterException) exception).getErrorCode() == StatusCodeMessageUtils.STATUS_IS_DUPLICATE) {
					showErrorMessage(getString(R.string.status_is_duplicate), false);
//				} else if (exception instanceof  ShortenException) {
//					//saveDrafts(item, failed_account_ids, false);
				} else {
                    final ContentValues accountIdsValues = new ContentValues();
                    accountIdsValues.put(Drafts.ACCOUNT_IDS, ListUtils.toString(failedAccountIds, ',', false));
                    mResolver.update(Drafts.CONTENT_URI, accountIdsValues, where.getSQL(), null);
					showErrorMessage(R.string.action_updating_status, exception, true);
                    displayTweetNotSendNotification();
				}
			} else {
				showOkMessage(R.string.status_updated, false);
                mResolver.delete(Drafts.CONTENT_URI, where.getSQL(), null);
				if (item.media != null) {
					for (final ParcelableMediaUpdate media : item.media) {
						final String path = getImagePathFromUri(this, Uri.parse(media.uri));
						if (path != null) {
                            if (!new File(path).delete()) {
                                Log.d(LOGTAG, String.format("unable to delete %s", path));
						    }
					    }
				    }
			    }
            }
            mTwitter.removeSendingDraftId(draftId);
			if (mPreferences.getBoolean(KEY_REFRESH_AFTER_TWEET, false)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
						mTwitter.refreshAll();
					}
                });
            }
		}
        stopForeground(false);
        mNotificationManager.cancel(NOTIFICATION_ID_UPDATE_STATUS);
	}

    private void displayTweetNotSendNotification() {
		final String title = getString(R.string.status_not_updated);
        final String message = getString(R.string.status_not_updated_summary);
        final Intent intent = new Intent(INTENT_ACTION_DRAFTS);
        final Notification notification = buildNotification(title, message, R.drawable.ic_stat_twittnuker, intent, null, false);
        mNotificationManager.notify(NOTIFICATION_ID_DRAFTS, notification);
    }

	private SingleResponse<ParcelableDirectMessage> sendDirectMessage(final NotificationCompat.Builder builder,
                                                                      final long accountId, final long recipientId,
                                                                      final String text, final String imageUri) {
		final Twitter twitter = getTwitterInstance(this, accountId, true, true);
        if (twitter == null) return SingleResponse.getInstance();
		try {
			final ParcelableDirectMessage directMessage;
			if (imageUri != null) {
				final String path = getImagePathFromUri(this, Uri.parse(imageUri));
				if (path == null) throw new FileNotFoundException();
				final BitmapFactory.Options o = new BitmapFactory.Options();
				o.inJustDecodeBounds = true;
				BitmapFactory.decodeFile(path, o);
				final File file = new File(path);
                BitmapUtils.downscaleImageIfNeeded(file, 100);
				final ContentLengthInputStream is = new ContentLengthInputStream(file);
				is.setReadListener(new MessageMediaUploadListener(this, mNotificationManager, builder, text));
				final MediaUploadResponse uploadResp = twitter.uploadMedia(file.getName(), is, o.outMimeType);
				directMessage = new ParcelableDirectMessage(twitter.sendDirectMessage(recipientId, text,
						uploadResp.getId()), accountId, true);
                if (!file.delete()) {
                    Log.d(LOGTAG, String.format("unable to delete %s", path));
                }
			} else {
				directMessage = new ParcelableDirectMessage(twitter.sendDirectMessage(recipientId, text), accountId,
						true);
			}
            Utils.setLastSeen(this, recipientId, System.currentTimeMillis());


			return SingleResponse.getInstance(directMessage);
		} catch (final IOException e) {
			return SingleResponse.getInstance(e);
		} catch (final TwitterException e) {
			return SingleResponse.getInstance(e);
		}
	}

	private void showToast(final int resId, final int duration) {
		mHandler.post(new ToastRunnable(this, resId, duration));
	}

    private List<SingleResponse<ParcelableStatus>> updateStatus(final Builder builder,
                                                                final ParcelableStatusUpdate statusUpdate) {
        final ArrayList<ContentValues> hashTagValues = new ArrayList<>();
        final Collection<String> hashTags = extractor.extractHashtags(statusUpdate.text);
        for (final String hashTag : hashTags) {
			final ContentValues values = new ContentValues();
            values.put(CachedHashtags.NAME, hashTag);
            hashTagValues.add(values);
		}
		boolean notReplyToOther = false;
		mResolver.bulkInsert(CachedHashtags.CONTENT_URI,
                hashTagValues.toArray(new ContentValues[hashTagValues.size()]));

        final List<SingleResponse<ParcelableStatus>> results = new ArrayList<>();

		if (statusUpdate.accounts.length == 0) return Collections.emptyList();

		try {
			final boolean hasMedia = statusUpdate.media != null && statusUpdate.media.length > 0;
			final String imagePath = hasMedia ? getImagePathFromUri(this, Uri.parse(statusUpdate.media[0].uri)) : null;
            final File imageFile = imagePath != null ? new File(imagePath) : null;

			String uploadResultUrl = null;

			if (mUseUploader && imageFile != null && imageFile.exists()) {
				uploadResultUrl = uploadMedia(imageFile, statusUpdate.accounts, statusUpdate.text);
			}

            final String unshortenedContent = mUseUploader && uploadResultUrl != null ? getImageUploadStatus(
					new String[] { uploadResultUrl.toString() }, statusUpdate.text) : statusUpdate.text;

			final boolean shouldShorten = mValidator.getTweetLength(unshortenedContent) > mValidator.getMaxTweetLength();
			Map<Long, ShortenedStatusModel> shortenedStatuses = null;

			if (shouldShorten && mUseShortener) {
				if (mShortener.equals(SERVICE_SHORTENER_HOTOTIN)) {
					shortenedStatuses = postHototIn(statusUpdate);
					if (shortenedStatuses == null) throw new ShortenException(this);
				} else if (mShortener.equals(SERVICE_SHORTENER_TWITLONGER)) {
					shortenedStatuses = postTwitlonger(statusUpdate);
					if (shortenedStatuses == null) throw new ShortenException(this);
				} else {
					throw new IllegalArgumentException("BackgroundOperationService.java#updateStatus()");
				}
			}

			if (shouldShorten) {
				if (!mUseShortener)
					throw new StatusTooLongException(this);
				else if (unshortenedContent == null) throw new ShortenException(this);
			}
			if (!mUseUploader && statusUpdate.media != null) {
				for (final ParcelableMediaUpdate media : statusUpdate.media) {
					final String path = getImagePathFromUri(this, Uri.parse(media.uri));
					final File file = path != null ? new File(path) : null;
					if (file != null && file.exists()) {
                        BitmapUtils.downscaleImageIfNeeded(file, 95);
					}
				}
			}
            for (final ParcelableAccount account : statusUpdate.accounts) {
				String shortenedContent = "";
				ShortenedStatusModel shortenedStatusModel = null;
				final Twitter twitter = getTwitterInstance(this, account.account_id, true, true);

				if (shouldShorten && mUseShortener && shortenedStatuses != null) {
					shortenedStatusModel = shortenedStatuses.get(account.account_id);
					shortenedContent = shortenedStatusModel.getText();
				}
				final StatusUpdate status = new StatusUpdate(shouldShorten && mUseShortener ? shortenedContent
						: unshortenedContent);
				status.setInReplyToStatusId(statusUpdate.in_reply_to_status_id);
				if (statusUpdate.location != null) {
					status.setLocation(ParcelableLocation.toGeoLocation(statusUpdate.location));
				}
                if (!mUseUploader && hasMedia) {
					final BitmapFactory.Options o = new BitmapFactory.Options();
					o.inJustDecodeBounds = true;
                    final long[] mediaIds = new long[statusUpdate.media.length];
					ContentLengthInputStream is = null;
					try {
						for (int i = 0, j = mediaIds.length; i < j; i++) {
							final ParcelableMediaUpdate media = statusUpdate.media[i];
							final String path = getImagePathFromUri(this, Uri.parse(media.uri));
							if (path == null) throw new FileNotFoundException();
							BitmapFactory.decodeFile(path, o);
							final File file = new File(path);
							is = new ContentLengthInputStream(file);
							is.setReadListener(new StatusMediaUploadListener(this, mNotificationManager, builder,
									statusUpdate));
                            final MediaUploadResponse uploadResp = twitter.uploadMedia(file.getName(), is,
                                    o.outMimeType);
                            mediaIds[i] = uploadResp.getId();
						}
                    } catch (final FileNotFoundException e) {
                        Log.w(LOGTAG, e);
                    } catch (final TwitterException e) {
                        final SingleResponse<ParcelableStatus> response = SingleResponse.getInstance(e);
                        results.add(response);
                        continue;
                    } finally {
                        IoUtils.closeSilently(is);
					}
					status.mediaIds(mediaIds);
				}
				status.setPossiblySensitive(statusUpdate.is_possibly_sensitive);

				if (twitter == null) {
					results.add(new SingleResponse<ParcelableStatus>(null, new NullPointerException()));
					continue;
				}
				try {
                    final Status resultStatus = twitter.updateStatus(status);

					//Update Twitlonger statuses
					if (shouldShorten && mUseShortener && mShortener.equals(SERVICE_SHORTENER_TWITLONGER)) {
						TweetShortenerUtils.updateTwitlonger(shortenedStatusModel, resultStatus.getId(), twitter);
					}
					Utils.setLastSeen(this, resultStatus.getUserMentionEntities(), System.currentTimeMillis());
					if (!notReplyToOther) {
                        final long inReplyToUserId = resultStatus.getInReplyToUserId();
						if (inReplyToUserId <= 0) {
							notReplyToOther = true;
						}
					}
                    final ParcelableStatus result = new ParcelableStatus(resultStatus, account.account_id, false);
                    results.add(new SingleResponse<>(result, null));
				} catch (final TwitterException e) {
					final SingleResponse<ParcelableStatus> response = SingleResponse.getInstance(e);
					results.add(response);
				}
			}
		} catch (final UpdateStatusException e) {
			final SingleResponse<ParcelableStatus> response = SingleResponse.getInstance(e);
			results.add(response);
		}
		return results;
	}

    private static Notification updateSendDirectMessageNotification(final Context context,
			final NotificationCompat.Builder builder, final int progress, final String message) {
		builder.setContentTitle(context.getString(R.string.sending_direct_message));
		if (message != null) {
			builder.setContentText(message);
		}
		builder.setSmallIcon(R.drawable.ic_stat_send);
		builder.setProgress(100, progress, progress >= 100 || progress <= 0);
		builder.setOngoing(true);
		return builder.build();
	}

	private static Notification updateUpdateStatusNotification(final Context context,
			final NotificationCompat.Builder builder, final int progress, final ParcelableStatusUpdate status) {
        builder.setContentTitle(context.getString(R.string.updating_status_notification));
		if (status != null) {
			builder.setContentText(status.text);
		}
        builder.setSmallIcon(R.drawable.ic_stat_send);
        builder.setProgress(100, progress, progress >= 100 || progress <= 0);
        builder.setOngoing(true);
        return builder.build();
	}

	private static class ToastRunnable implements Runnable {
		private final Context context;
		private final int resId;
		private final int duration;

		public ToastRunnable(final Context context, final int resId, final int duration) {
			this.context = context;
			this.resId = resId;
			this.duration = duration;
		}

		@Override
		public void run() {
			Toast.makeText(context, resId, duration).show();

		}

	}

	private Notification updateUploadStatusNotification(final NotificationCompat.Builder builder, final int progress) {
		builder.setContentTitle(getString(R.string.uploading_image));
		builder.setContentText((progress < 100 ? progress : 100) + "%");
		builder.setSmallIcon(R.drawable.ic_stat_send);
		builder.setProgress(100, progress, progress >= 100 || progress <= 0);
		final Notification notification = builder.build();
		mNotificationManager.notify(NOTIFICATION_ID_UPLOAD_MEDIA, notification);
		return notification;
	}

	private Map<Long, ShortenedStatusModel> postTwitlonger(ParcelableStatusUpdate pstatus) {
		final Notification notification = buildNotification(getString(R.string.shortening),
				getString(R.string.shortening_twitlonger), R.drawable.ic_stat_twittnuker, null, null, true);
		mNotificationManager.notify(NOTIFICATION_ID_SHORTENING, notification);

		Map<Long, ShortenedStatusModel> statuses;
		try {
			statuses = TweetShortenerUtils.postTwitlonger(this, pstatus);
		} finally {
			mNotificationManager.cancel(NOTIFICATION_ID_SHORTENING);
		}

		if (statuses == null || statuses.isEmpty()) {
			final Intent intent = new Intent(INTENT_ACTION_DRAFTS);
			final Notification errorNotification = buildNotification(getString(R.string.shortening),
					getString(R.string.error_twitlonger), R.drawable.ic_stat_twittnuker, intent, null, false);
			mNotificationManager.notify(NOTIFICATION_ID_SHORTENING, errorNotification);
			return null;
		}
		return statuses;
	}

	private Map<Long, ShortenedStatusModel> postHototIn(ParcelableStatusUpdate pstatus) {

		final Notification notification = buildNotification(getString(R.string.shortening),
				getString(R.string.shortening_hototin), R.drawable.ic_stat_twittnuker, null, null, true);
		mNotificationManager.notify(NOTIFICATION_ID_SHORTENING, notification);

		Map<Long, ShortenedStatusModel> statuses;
		try {
			statuses = TweetShortenerUtils.shortWithHototin(this, pstatus);
		} finally {
			mNotificationManager.cancel(NOTIFICATION_ID_SHORTENING);
		}

		if (statuses == null || statuses.isEmpty()) {
			final Intent intent = new Intent(INTENT_ACTION_DRAFTS);
			final Notification errorNotification = buildNotification(getString(R.string.shortening),
					getString(R.string. error_hototin), R.drawable.ic_stat_twittnuker, intent, null, false);
			mNotificationManager.notify(NOTIFICATION_ID_SHORTENING, errorNotification);
			return null;
		}
		return statuses;
	}

	private String uploadMedia(File file, ParcelableAccount[] accounts, String message) throws UploadException {
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		String url = null;
		Configuration conf;

		long accountId = -1;
		if(accounts != null && accounts.length > 0) {
			accountId = accounts[0].account_id;
		}

		ContentLengthInputStream is;
		startForeground(NOTIFICATION_ID_UPLOAD_MEDIA, updateUploadStatusNotification(builder, 0));
		try {
			is = new ContentLengthInputStream(file);
			is.setReadListener(new ReadListener() {

				int percent;

				@Override
				public void onRead(final long length, final long position) {
					final int percent = length > 0 ? (int) (position * 100 / length) : 0;
					if (this.percent != percent) {
						mNotificationManager.notify(NOTIFICATION_ID_UPLOAD_MEDIA,
								updateUploadStatusNotification(builder, percent));
					}
					this.percent = percent;
				}
			});

			if (mUploader.equals(SERVICE_UPLOADER_TWIPPLE)) {
				Twitter twitter = getTwitterInstance(this, accountId, MediaProvider.TWIPPLE.toString(), "");
				if (twitter == null) throw new UploadException(this);

				conf = twitter.getConfiguration();

				ImageUpload imageUpload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWIPPLE);
				try {
					url = imageUpload.upload(file.getName(), is, message);
				} catch (TwitterException e) {
					throw new UploadException(this);
				}
			}
			is.close();
		} catch (FileNotFoundException e) {
			throw new UploadException(this);
		} catch (IOException e) {
			throw new UploadException(this);
		}

		if (isEmpty(url)) throw new UploadException(this);
		return url;
	}

	static class UploadException extends UpdateStatusException {
		private static final long serialVersionUID = 8596614696393917525L;

		public UploadException(final Context context) {
			super(context.getString(R.string.error_message_image_upload_failed));
		}

		public UploadException(final String message) {
			super(message);
		}

	}

	static class MessageMediaUploadListener implements ReadListener {
		private final Context context;
		private final NotificationManager manager;

		int percent;

		private final Builder builder;
		private final String message;

		MessageMediaUploadListener(final Context context, final NotificationManager manager,
								   final NotificationCompat.Builder builder, final String message) {
			this.context = context;
			this.manager = manager;
			this.builder = builder;
			this.message = message;
		}

		@Override
		public void onRead(final long length, final long position) {
			final int percent = length > 0 ? (int) (position * 100 / length) : 0;
			if (this.percent != percent) {
				manager.notify(NOTIFICATION_ID_SEND_DIRECT_MESSAGE,
                        updateSendDirectMessageNotification(context, builder, percent, message));
			}
			this.percent = percent;
		}
	}

    static class StatusMediaUploadListener implements ReadListener {
        private final Context context;
        private final NotificationManager manager;

        int percent;

        private final Builder builder;
        private final ParcelableStatusUpdate statusUpdate;

        StatusMediaUploadListener(final Context context, final NotificationManager manager,
                final NotificationCompat.Builder builder, final ParcelableStatusUpdate statusUpdate) {
            this.context = context;
            this.manager = manager;
            this.builder = builder;
            this.statusUpdate = statusUpdate;
        }

        @Override
        public void onRead(final long length, final long position) {
            final int percent = length > 0 ? (int) (position * 100 / length) : 0;
            if (this.percent != percent) {
                manager.notify(NOTIFICATION_ID_UPDATE_STATUS,
                        updateUpdateStatusNotification(context, builder, percent, statusUpdate));
                }
            this.percent = percent;
        }
    }

	static class StatusTooLongException extends UpdateStatusException {
		private static final long serialVersionUID = -6469920130856384219L;

		public StatusTooLongException(final Context context) {
			super(context.getString(R.string.error_message_status_too_long));
		}
	}

	static class ShortenException extends UpdateStatusException {
		private static final long serialVersionUID = 3075877185536740034L;

		public ShortenException(final Context context) {
			super(context.getString(R.string.error_message_tweet_shorten_failed));
		}
	}

	static class UpdateStatusException extends Exception {
		private static final long serialVersionUID = -1267218921727097910L;

		public UpdateStatusException(final String message) {
			super(message);
		}
	}
}
