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

package de.vanita5.twittnuker.view.holder

import android.graphics.Typeface
import android.support.v4.content.ContextCompat
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ViewHolder
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.RequestManager
import kotlinx.android.synthetic.main.list_item_status.view.*
import org.mariotaku.ktextension.*
import de.vanita5.microblog.library.mastodon.annotation.StatusVisibility
import de.vanita5.twittnuker.Constants.*
import de.vanita5.twittnuker.R
import de.vanita5.twittnuker.TwittnukerConstants.USER_TYPE_FANFOU_COM
import de.vanita5.twittnuker.adapter.iface.IStatusesAdapter
import de.vanita5.twittnuker.constant.SharedPreferenceConstants.VALUE_LINK_HIGHLIGHT_OPTION_CODE_NONE
import de.vanita5.twittnuker.extension.loadProfileImage
import de.vanita5.twittnuker.extension.model.applyTo
import de.vanita5.twittnuker.extension.model.quoted_user_acct
import de.vanita5.twittnuker.extension.model.retweeted_by_user_acct
import de.vanita5.twittnuker.extension.model.user_acct
import de.vanita5.twittnuker.graphic.like.LikeAnimationDrawable
import de.vanita5.twittnuker.model.ParcelableLocation
import de.vanita5.twittnuker.model.ParcelableMedia
import de.vanita5.twittnuker.model.ParcelableStatus
import de.vanita5.twittnuker.model.UserKey
import de.vanita5.twittnuker.task.CreateFavoriteTask
import de.vanita5.twittnuker.task.DestroyFavoriteTask
import de.vanita5.twittnuker.task.RetweetStatusTask
import de.vanita5.twittnuker.text.TwidereClickableSpan
import de.vanita5.twittnuker.util.*
import de.vanita5.twittnuker.util.HtmlEscapeHelper.toPlainText
import de.vanita5.twittnuker.util.Utils.getUserTypeIconRes
import de.vanita5.twittnuker.view.ShapedImageView
import de.vanita5.twittnuker.view.holder.iface.IStatusViewHolder
import java.lang.ref.WeakReference

class StatusViewHolder(private val adapter: IStatusesAdapter<*>, itemView: View) : ViewHolder(itemView), IStatusViewHolder {

    override val profileImageView: ShapedImageView by lazy { itemView.profileImage }
    override val profileTypeView: ImageView by lazy { itemView.profileType }

    private val itemContent by lazy { itemView.itemContent }
    private val mediaPreview by lazy { itemView.mediaPreview }
    private val statusContentUpperSpace by lazy { itemView.statusContentUpperSpace }
    private val summaryView by lazy { itemView.summary }
    private val textView by lazy { itemView.text }
    private val nameView by lazy { itemView.name }
    private val itemMenu by lazy { itemView.itemMenu }
    private val statusInfoLabel by lazy { itemView.statusInfoLabel }
    private val statusInfoIcon by lazy { itemView.statusInfoIcon }
    private val quotedNameView by lazy { itemView.quotedName }
    private val timeView by lazy { itemView.time }
    private val replyCountView by lazy { itemView.replyCount }
    private val retweetCountView by lazy { itemView.retweetCount }
    private val quotedView by lazy { itemView.quotedView }
    private val quotedTextView by lazy { itemView.quotedText }
    private val actionButtons by lazy { itemView.actionButtons }
    private val mediaLabel by lazy { itemView.mediaLabel }
    private val quotedMediaLabel by lazy { itemView.quotedMediaLabel }
    private val statusContentLowerSpace by lazy { itemView.statusContentLowerSpace }
    private val quotedMediaPreview by lazy { itemView.quotedMediaPreview }
    private val favoriteIcon by lazy { itemView.favoriteIcon }
    private val retweetIcon by lazy { itemView.retweetIcon }
    private val favoriteCountView by lazy { itemView.favoriteCount }
    private val replyButton by lazy { itemView.reply }
    private val retweetButton by lazy { itemView.retweet }
    private val favoriteButton by lazy { itemView.favorite }

    private val eventListener: EventListener

    private var statusClickListener: IStatusViewHolder.StatusClickListener? = null


    init {
        this.eventListener = EventListener(this)

        if (adapter.mediaPreviewEnabled) {
            View.inflate(mediaPreview.context, R.layout.layout_card_media_preview,
                    itemView.mediaPreview)
            View.inflate(quotedMediaPreview.context, R.layout.layout_card_media_preview,
                    itemView.quotedMediaPreview)
        }

    }


    fun displaySampleStatus() {
        val profileImageEnabled = adapter.profileImageEnabled
        profileImageView.visibility = if (profileImageEnabled) View.VISIBLE else View.GONE
        statusContentUpperSpace.visibility = View.VISIBLE

        adapter.requestManager.loadProfileImage(itemView.context, R.drawable.ic_account_logo_twitter,
                adapter.profileImageStyle, profileImageView.cornerRadius,
                profileImageView.cornerRadiusRatio).into(profileImageView)
        nameView.name = TWITTNUKER_PREVIEW_NAME
        nameView.screenName = "@$TWITTNUKER_PREVIEW_SCREEN_NAME"
        nameView.updateText(adapter.bidiFormatter)
        summaryView.hideIfEmpty()
        if (adapter.linkHighlightingStyle == VALUE_LINK_HIGHLIGHT_OPTION_CODE_NONE) {
            textView.spannable = toPlainText(TWITTNUKER_PREVIEW_TEXT_HTML)
        } else {
            val linkify = adapter.twidereLinkify
            val text = HtmlSpanBuilder.fromHtml(TWITTNUKER_PREVIEW_TEXT_HTML)
            linkify.applyAllLinks(text, null, -1, false, adapter.linkHighlightingStyle, true)
            textView.spannable = text
        }
        timeView.time = System.currentTimeMillis()
        val showCardActions = isCardActionsShown
        if (adapter.mediaPreviewEnabled) {
            mediaPreview.visibility = View.VISIBLE
            mediaLabel.visibility = View.GONE
        } else {
            mediaPreview.visibility = View.GONE
            mediaLabel.visibility = View.VISIBLE
        }
        actionButtons.visibility = if (showCardActions) View.VISIBLE else View.GONE
        itemMenu.visibility = if (showCardActions) View.VISIBLE else View.GONE
        statusContentLowerSpace.visibility = if (showCardActions) View.GONE else View.VISIBLE
        quotedMediaPreview.visibility = View.GONE
        quotedMediaLabel.visibility = View.GONE
        mediaPreview.displayMedia(R.drawable.twittnuker_feature_graphic)
    }

    override fun display(status: ParcelableStatus, displayInReplyTo: Boolean,
            displayPinned: Boolean) {

        val context = itemView.context
        val requestManager = adapter.requestManager
        val twitter = adapter.twitterWrapper
        val linkify = adapter.twidereLinkify
        val formatter = adapter.bidiFormatter
        val colorNameManager = adapter.userColorNameManager
        val nameFirst = adapter.nameFirst
        val showCardActions = isCardActionsShown

        actionButtons.visibility = if (showCardActions) View.VISIBLE else View.GONE
        itemMenu.visibility = if (showCardActions) View.VISIBLE else View.GONE
        statusContentLowerSpace.visibility = if (showCardActions) View.GONE else View.VISIBLE

        val replyCount = status.reply_count
        val retweetCount = status.retweet_count
        val favoriteCount = status.favorite_count

        if (displayPinned && status.is_pinned_status) {
            statusInfoLabel.setText(R.string.pinned_status)
            statusInfoIcon.setImageResource(R.drawable.ic_activity_action_pinned)
            statusInfoLabel.visibility = View.VISIBLE
            statusInfoIcon.visibility = View.VISIBLE

            statusContentUpperSpace.visibility = View.GONE
        } else if (TwitterCardUtils.isPoll(status)) {
            statusInfoLabel.setText(R.string.label_poll)
            statusInfoIcon.setImageResource(R.drawable.ic_activity_action_poll)
            statusInfoLabel.visibility = View.VISIBLE
            statusInfoIcon.visibility = View.VISIBLE

            statusContentUpperSpace.visibility = View.GONE
        } else if (status.retweet_id != null) {
            val retweetedBy = colorNameManager.getDisplayName(status.retweeted_by_user_key!!,
                    status.retweeted_by_user_name, status.retweeted_by_user_acct!!, nameFirst)
            statusInfoLabel.spannable = context.getString(R.string.name_retweeted, formatter.unicodeWrap(retweetedBy))
            statusInfoIcon.setImageResource(R.drawable.ic_activity_action_retweet)
            statusInfoLabel.visibility = View.VISIBLE
            statusInfoIcon.visibility = View.VISIBLE

            statusContentUpperSpace.visibility = View.GONE
        } else if (status.in_reply_to_status_id != null && status.in_reply_to_user_key != null && displayInReplyTo) {
            if (status.in_reply_to_name != null && status.in_reply_to_screen_name != null) {
                val inReplyTo = colorNameManager.getDisplayName(status.in_reply_to_user_key!!,
                        status.in_reply_to_name, status.in_reply_to_screen_name, nameFirst)
                statusInfoLabel.spannable = context.getString(R.string.in_reply_to_name, formatter.unicodeWrap(inReplyTo))
            } else {
                statusInfoLabel.spannable = context.getString(R.string.label_status_type_reply)
            }
            statusInfoIcon.setImageResource(R.drawable.ic_activity_action_reply)
            statusInfoLabel.visibility = View.VISIBLE
            statusInfoIcon.visibility = View.VISIBLE

            statusContentUpperSpace.visibility = View.GONE
        } else {
            statusInfoLabel.visibility = View.GONE
            statusInfoIcon.visibility = View.GONE

            statusContentUpperSpace.visibility = View.VISIBLE
        }

        val skipLinksInText = status.extras?.support_entities ?: false
        if (status.is_quote) {

            quotedView.visibility = View.VISIBLE

            val quoteContentAvailable = status.quoted_text_plain != null && status.quoted_text_unescaped != null
            val isFanfouStatus = status.account_key.host == USER_TYPE_FANFOU_COM
            if (quoteContentAvailable && !isFanfouStatus) {
                quotedNameView.visibility = View.VISIBLE
                quotedTextView.visibility = View.VISIBLE

                val quoted_user_key = status.quoted_user_key!!
                quotedNameView.name =
                        status.quoted_user_name
                quotedNameView.screenName = "@${status.quoted_user_acct}"

                val quotedDisplayEnd = status.extras?.quoted_display_text_range?.getOrNull(1) ?: -1
                val quotedText: CharSequence
                if (adapter.linkHighlightingStyle != VALUE_LINK_HIGHLIGHT_OPTION_CODE_NONE) {
                    quotedText = SpannableStringBuilder.valueOf(status.quoted_text_unescaped)
                    status.quoted_spans?.applyTo(quotedText)
                    linkify.applyAllLinks(quotedText, status.account_key, layoutPosition.toLong(),
                            status.is_possibly_sensitive, adapter.linkHighlightingStyle,
                            skipLinksInText)
                } else {
                    quotedText = status.quoted_text_unescaped
                }
                if (quotedDisplayEnd != -1 && quotedDisplayEnd <= quotedText.length) {
                    quotedTextView.spannable = quotedText.subSequence(0, quotedDisplayEnd)
                } else {
                    quotedTextView.spannable = quotedText
                }
                quotedTextView.hideIfEmpty()

                val quoted_user_color = colorNameManager.getUserColor(quoted_user_key)
                if (quoted_user_color != 0) {
                    quotedView.drawStart(quoted_user_color)
                } else {
                    quotedView.drawStart(ThemeUtils.getColorFromAttribute(context,
                            R.attr.quoteIndicatorBackgroundColor))
                }

                displayQuotedMedia(requestManager, status)
            } else {
                quotedNameView.visibility = View.GONE
                quotedTextView.visibility = View.VISIBLE

                if (quoteContentAvailable) {
                    displayQuotedMedia(requestManager, status)
                } else {
                    quotedMediaPreview.visibility = View.GONE
                    quotedMediaLabel.visibility = View.GONE
                }

                quotedTextView.spannable = if (!quoteContentAvailable) {
                    // Display 'not available' label
                    SpannableString.valueOf(context.getString(R.string.label_status_not_available)).apply {
                        setSpan(ForegroundColorSpan(ThemeUtils.getColorFromAttribute(context,
                                android.R.attr.textColorTertiary, textView.currentTextColor)), 0,
                                length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else {
                    // Display 'original status' label
                    context.getString(R.string.label_original_status)
                }

                quotedView.drawStart(ThemeUtils.getColorFromAttribute(context,
                        R.attr.quoteIndicatorBackgroundColor))
            }

            itemContent.drawStart(colorNameManager.getUserColor(status.user_key))
        } else {
            quotedView.visibility = View.GONE

            val userColor = colorNameManager.getUserColor(status.user_key)

            if (status.is_retweet) {
                val retweetUserColor = colorNameManager.getUserColor(status.retweeted_by_user_key!!)
                if (retweetUserColor == 0) {
                    itemContent.drawStart(userColor)
                } else if (userColor == 0) {
                    itemContent.drawStart(retweetUserColor)
                } else {
                    itemContent.drawStart(retweetUserColor, userColor)
                }
            } else {
                itemContent.drawStart(userColor)
            }
        }

        timeView.time = if (status.is_retweet) {
            status.retweet_timestamp
        } else {
            status.timestamp
        }

        nameView.name = status.user_name
        nameView.screenName = "@${status.user_acct}"

        if (adapter.profileImageEnabled) {
            profileImageView.visibility = View.VISIBLE
            requestManager.loadProfileImage(context, status, adapter.profileImageStyle,
                    profileImageView.cornerRadius, profileImageView.cornerRadiusRatio,
                    adapter.profileImageSize).into(profileImageView)

            profileTypeView.setImageResource(getUserTypeIconRes(status.user_is_verified, status.user_is_protected))
            profileTypeView.visibility = View.VISIBLE
        } else {
            profileImageView.visibility = View.GONE

            profileTypeView.setImageDrawable(null)
            profileTypeView.visibility = View.GONE
        }

        if (adapter.showAccountsColor) {
            itemContent.drawEnd(status.account_color)
        } else {
            itemContent.drawEnd()
        }

        if (status.media.isNotNullOrEmpty()) {

            mediaLabel.displayMediaLabel(status.card_name, status.media, status.location,
                    status.place_full_name, status.is_possibly_sensitive)

            if (!adapter.sensitiveContentEnabled && status.is_possibly_sensitive) {
                // Sensitive content, show label instead of media view
                mediaLabel.visibility = View.VISIBLE
                mediaLabel.contentDescription = status.media?.firstOrNull()?.alt_text
                mediaPreview.visibility = View.GONE
            } else if (!adapter.mediaPreviewEnabled) {
                // Media preview disabled, just show label
                mediaLabel.visibility = View.VISIBLE
                mediaLabel.contentDescription = status.media?.firstOrNull()?.alt_text
                mediaPreview.visibility = View.GONE
            } else {
                // Show media
                mediaLabel.visibility = View.GONE
                mediaPreview.visibility = View.VISIBLE

                mediaPreview.displayMedia(requestManager = requestManager,
                        media = status.media, accountKey = status.account_key,
                        mediaClickListener = this)
            }
        } else {
            // No media, hide all related views
            mediaLabel.visibility = View.GONE
            mediaPreview.visibility = View.GONE
        }

        summaryView.spannable = status.extras?.summary_text
        summaryView.hideIfEmpty()

        val text: CharSequence
        val displayEnd: Int
        if (!summaryView.empty && !isFullTextVisible) {
            text = SpannableStringBuilder.valueOf(context.getString(R.string.label_status_show_more)).apply {
                setSpan(object : TwidereClickableSpan(adapter.linkHighlightingStyle) {
                    override fun onClick(widget: View?) {
                        showFullText()
                    }
                }, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            displayEnd = -1
        } else if (adapter.linkHighlightingStyle != VALUE_LINK_HIGHLIGHT_OPTION_CODE_NONE) {
            text = SpannableStringBuilder.valueOf(status.text_unescaped).apply {
                status.spans?.applyTo(this)
                linkify.applyAllLinks(this, status.account_key, layoutPosition.toLong(),
                        status.is_possibly_sensitive, adapter.linkHighlightingStyle,
                        skipLinksInText)
            }
            displayEnd = status.extras?.display_text_range?.getOrNull(1) ?: -1
        } else {
            text = status.text_unescaped
            displayEnd = status.extras?.display_text_range?.getOrNull(1) ?: -1
        }

        if (displayEnd != -1 && displayEnd <= text.length) {
            textView.spannable = text.subSequence(0, displayEnd)
        } else {
            textView.spannable = text
        }
        textView.hideIfEmpty()

        if (replyCount > 0) {
            replyCountView.spannable = UnitConvertUtils.calculateProperCount(replyCount)
        } else {
            replyCountView.spannable = null
        }
        replyCountView.hideIfEmpty()

        when (status.extras?.visibility) {
            StatusVisibility.PRIVATE -> {
                retweetIcon.setImageResource(R.drawable.ic_action_lock)
            }
            StatusVisibility.DIRECT -> {
                retweetIcon.setImageResource(R.drawable.ic_action_message)
            }
            else -> {
                retweetIcon.setImageResource(R.drawable.ic_action_retweet)
            }
        }

        if (twitter.isDestroyingStatus(status.account_key, status.my_retweet_id)) {
            retweetIcon.isActivated = false
        } else {
            val creatingRetweet = RetweetStatusTask.isCreatingRetweet(status.account_key, status.id)
            retweetIcon.isActivated = creatingRetweet || status.retweeted ||
                    Utils.isMyRetweet(status.account_key, status.retweeted_by_user_key,
                            status.my_retweet_id)
        }

        if (retweetCount > 0) {
            retweetCountView.spannable = UnitConvertUtils.calculateProperCount(retweetCount)
        } else {
            retweetCountView.spannable = null
        }
        retweetCountView.hideIfEmpty()

        if (DestroyFavoriteTask.isDestroyingFavorite(status.account_key, status.id)) {
            favoriteIcon.isActivated = false
        } else {
            val creatingFavorite = CreateFavoriteTask.isCreatingFavorite(status.account_key, status.id)
            favoriteIcon.isActivated = creatingFavorite || status.is_favorite
        }

        if (favoriteCount > 0) {
            favoriteCountView.spannable = UnitConvertUtils.calculateProperCount(favoriteCount)
        } else {
            favoriteCountView.spannable = null
        }
        favoriteCountView.hideIfEmpty()

        nameView.updateText(formatter)
        quotedNameView.updateText(formatter)

    }

    private fun displayQuotedMedia(requestManager: RequestManager, status: ParcelableStatus) {
        if (status.quoted_media?.isNotEmpty() ?: false) {

            quotedMediaLabel.displayMediaLabel(null, status.quoted_media, null, null,
                    status.is_possibly_sensitive)

            if (!adapter.sensitiveContentEnabled && status.is_possibly_sensitive) {
                // Sensitive content, show label instead of media view
                quotedMediaPreview.visibility = View.GONE
                quotedMediaLabel.visibility = View.VISIBLE
            } else if (!adapter.mediaPreviewEnabled) {
                // Media preview disabled, just show label
                quotedMediaPreview.visibility = View.GONE
                quotedMediaLabel.visibility = View.VISIBLE
            } else if (status.media.isNotNullOrEmpty()) {
                // Already displaying media, show label only
                quotedMediaPreview.visibility = View.GONE
                quotedMediaLabel.visibility = View.VISIBLE
            } else {
                // Show media
                quotedMediaPreview.visibility = View.VISIBLE
                quotedMediaLabel.visibility = View.GONE

                quotedMediaPreview.displayMedia(requestManager = requestManager,
                        media = status.quoted_media, accountKey = status.account_key,
                        mediaClickListener = this)
            }
        } else {
            // No media, hide all related views
            quotedMediaPreview.visibility = View.GONE
            quotedMediaLabel.visibility = View.GONE
        }
    }

    override fun onMediaClick(view: View, current: ParcelableMedia, accountKey: UserKey?, id: Long) {
        if (view.parent == quotedMediaPreview) {
            statusClickListener?.onQuotedMediaClick(this, view, current, layoutPosition)
        } else {
            statusClickListener?.onMediaClick(this, view, current, layoutPosition)
        }
    }

    fun setOnClickListeners() {
        setStatusClickListener(adapter.statusClickListener)
    }

    override fun setStatusClickListener(listener: IStatusViewHolder.StatusClickListener?) {
        statusClickListener = listener
        itemContent.setOnClickListener(eventListener)
        itemContent.setOnLongClickListener(eventListener)

        itemMenu.setOnClickListener(eventListener)
        profileImageView.setOnClickListener(eventListener)
        replyButton.setOnClickListener(eventListener)
        retweetButton.setOnClickListener(eventListener)
        favoriteButton.setOnClickListener(eventListener)
        retweetButton.setOnLongClickListener(eventListener)
        favoriteButton.setOnLongClickListener(eventListener)

        mediaLabel.setOnClickListener(eventListener)

        quotedView.setOnClickListener(eventListener)
    }


    override fun setTextSize(textSize: Float) {
        nameView.setPrimaryTextSize(textSize)
        quotedNameView.setPrimaryTextSize(textSize)
        summaryView.textSize = textSize
        textView.textSize = textSize
        quotedTextView.textSize = textSize
        nameView.setSecondaryTextSize(textSize * 0.85f)
        quotedNameView.setSecondaryTextSize(textSize * 0.85f)
        timeView.textSize = textSize * 0.85f
        statusInfoLabel.textSize = textSize * 0.75f

        mediaLabel.textSize = textSize * 0.95f
        quotedMediaLabel.textSize = textSize * 0.95f

        replyCountView.textSize = textSize
        retweetCountView.textSize = textSize
        favoriteCountView.textSize = textSize
    }

    fun setupViewOptions() {
        setTextSize(adapter.textSize)

        profileImageView.style = adapter.profileImageStyle

        mediaPreview.style = adapter.mediaPreviewStyle
        quotedMediaPreview.style = adapter.mediaPreviewStyle
        //        profileImageView.setStyle(adapter.getProfileImageStyle());

        val nameFirst = adapter.nameFirst
        nameView.nameFirst = nameFirst
        quotedNameView.nameFirst = nameFirst

        val favIcon: Int
        val favStyle: Int
        val favColor: Int
        val context = itemView.context
        if (adapter.useStarsForLikes) {
            favIcon = R.drawable.ic_action_star
            favStyle = LikeAnimationDrawable.Style.FAVORITE
            favColor = ContextCompat.getColor(context, R.color.highlight_favorite)
        } else {
            favIcon = R.drawable.ic_action_heart
            favStyle = LikeAnimationDrawable.Style.LIKE
            favColor = ContextCompat.getColor(context, R.color.highlight_like)
        }
        val icon = ContextCompat.getDrawable(context, favIcon)
        val drawable = LikeAnimationDrawable(icon,
                favoriteCountView.textColors.defaultColor, favColor, favStyle)
        drawable.mutate()
        favoriteIcon.setImageDrawable(drawable)
        timeView.showAbsoluteTime = adapter.showAbsoluteTime

        favoriteIcon.activatedColor = favColor

        nameView.applyFontFamily(adapter.lightFont)
        timeView.applyFontFamily(adapter.lightFont)
        summaryView.applyFontFamily(adapter.lightFont)
        textView.applyFontFamily(adapter.lightFont)
        mediaLabel.applyFontFamily(adapter.lightFont)

        quotedNameView.applyFontFamily(adapter.lightFont)
        quotedTextView.applyFontFamily(adapter.lightFont)
        quotedMediaLabel.applyFontFamily(adapter.lightFont)
    }

    override fun playLikeAnimation(listener: LikeAnimationDrawable.OnLikedListener) {
        var handled = false
        val drawable = favoriteIcon.drawable
        if (drawable is LikeAnimationDrawable) {
            drawable.setOnLikedListener(listener)
            drawable.start()
            handled = true
        }
        if (!handled) {
            listener.onLiked()
        }
    }

    private val isCardActionsShown: Boolean
        get() = adapter.isCardActionsShown(layoutPosition)

    private val isFullTextVisible: Boolean
        get() = adapter.isFullTextVisible(layoutPosition)

    private fun showCardActions() {
        adapter.showCardActions(layoutPosition)
    }

    private fun hideTempCardActions(): Boolean {
        adapter.showCardActions(RecyclerView.NO_POSITION)
        return !adapter.isCardActionsShown(RecyclerView.NO_POSITION)
    }

    private fun showFullText() {
        adapter.setFullTextVisible(layoutPosition, true)
    }

    private fun hideFullText(): Boolean {
        adapter.setFullTextVisible(layoutPosition, false)
        return !adapter.isFullTextVisible(RecyclerView.NO_POSITION)
    }

    private fun TextView.displayMediaLabel(cardName: String?, media: Array<ParcelableMedia?>?,
                                     location: ParcelableLocation?, placeFullName: String?,
                                     sensitive: Boolean) {
        if (sensitive) {
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(this, R.drawable.ic_label_warning, 0, 0, 0)
            setText(R.string.label_sensitive_content)
        } else if (media != null && media.isNotEmpty()) {
            val type = media.type
            if (type in videoTypes) {
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(this, R.drawable.ic_label_video, 0, 0, 0)
                setText(R.string.label_video)
            } else if (media.size > 1) {
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(this, R.drawable.ic_label_gallery, 0, 0, 0)
                setText(R.string.label_photos)
            } else {
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(this, R.drawable.ic_label_gallery, 0, 0, 0)
                setText(R.string.label_photo)
            }
        } else {
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(this, R.drawable.ic_label_gallery, 0, 0, 0)
            setText(R.string.label_media)
        }
        refreshDrawableState()
    }

    private val Array<ParcelableMedia?>.type: Int get() {
        forEach { if (it != null) return it.type }
        return 0
    }

    private fun hasVideo(media: Array<ParcelableMedia?>?): Boolean {
        if (media == null) return false
        return media.any { item ->
            if (item == null) return@any false
            return@any videoTypes.contains(item.type)
        }
    }

    internal class EventListener(holder: StatusViewHolder) : OnClickListener, OnLongClickListener {

        private val holderRef = WeakReference(holder)

        override fun onClick(v: View) {
            val holder = holderRef.get() ?: return
            val listener = holder.statusClickListener ?: return
            val position = holder.layoutPosition
            when (v) {
                holder.itemContent -> {
                    listener.onStatusClick(holder, position)
                }
                holder.quotedView -> {
                    listener.onQuotedStatusClick(holder, position)
                }
                holder.itemMenu -> {
                    listener.onItemMenuClick(holder, v, position)
                }
                holder.profileImageView -> {
                    listener.onUserProfileClick(holder, position)
                }
                holder.replyButton -> {
                    listener.onItemActionClick(holder, R.id.reply, position)
                }
                holder.retweetButton -> {
                    listener.onItemActionClick(holder, R.id.retweet, position)
                }
                holder.favoriteButton -> {
                    listener.onItemActionClick(holder, R.id.favorite, position)
                }
                holder.mediaLabel -> {
                    val firstMedia = holder.adapter.getStatus(position).media?.firstOrNull() ?: return
                    listener.onMediaClick(holder, v, firstMedia, position)
                }
            }
        }

        override fun onLongClick(v: View): Boolean {
            val holder = holderRef.get() ?: return false
            val listener = holder.statusClickListener ?: return false
            val position = holder.layoutPosition
            when (v) {
                holder.itemContent -> {
                    if (!holder.isCardActionsShown) {
                        holder.showCardActions()
                        return true
                    } else if (holder.hideTempCardActions()) {
                        return true
                    }
                    return listener.onStatusLongClick(holder, position)
                }
                holder.favoriteButton -> {
                    return listener.onItemActionLongClick(holder, R.id.favorite, position)
                }
                holder.retweetButton -> {
                    return listener.onItemActionLongClick(holder, R.id.retweet, position)
                }
            }
            return false
        }
    }

    companion object {
        const val layoutResource = R.layout.list_item_status

        private val videoTypes = intArrayOf(ParcelableMedia.Type.VIDEO, ParcelableMedia.Type.ANIMATED_GIF,
                ParcelableMedia.Type.EXTERNAL_PLAYER)
    }
}
