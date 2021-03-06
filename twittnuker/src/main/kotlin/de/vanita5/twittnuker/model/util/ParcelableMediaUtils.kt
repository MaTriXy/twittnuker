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

package de.vanita5.twittnuker.model.util

import org.mariotaku.ktextension.addAllTo
import org.mariotaku.ktextension.isNullOrEmpty
import org.mariotaku.ktextension.toIntOr
import de.vanita5.microblog.library.twitter.model.CardEntity
import de.vanita5.microblog.library.twitter.model.MediaEntity
import de.vanita5.microblog.library.twitter.model.Status
import de.vanita5.microblog.library.twitter.model.UrlEntity
import de.vanita5.twittnuker.extension.model.api.getEntityMedia
import de.vanita5.twittnuker.extension.model.api.gnusocial.toParcelable
import de.vanita5.twittnuker.extension.model.toParcelable
import de.vanita5.twittnuker.model.ParcelableMedia
import de.vanita5.twittnuker.model.ParcelableStatus
import de.vanita5.twittnuker.model.UserKey

object ParcelableMediaUtils {


    fun fromStatus(status: Status, accountKey: UserKey, accountType: String): Array<ParcelableMedia>? {
        return status.getEntityMedia() + status.getAttachmentMedia() + fromCard(status.card,
                status.urlEntities, status.mediaEntities, status.extendedMediaEntities, accountKey,
                accountType) + fromPhoto(status)
    }

    private fun fromPhoto(status: Status): Array<ParcelableMedia> {
        val photo = status.photo ?: return emptyArray()
        val media = ParcelableMedia()
        media.type = ParcelableMedia.Type.IMAGE
        media.url = photo.url
        media.page_url = photo.url
        media.media_url = photo.largeUrl
        media.preview_url = photo.imageUrl
        return arrayOf(media)
    }

    private fun Status.getAttachmentMedia(): Array<ParcelableMedia> {
        return attachments?.mapNotNull {
            it.toParcelable(externalUrl)
        }?.toTypedArray() ?: emptyArray()
    }

    private fun fromCard(card: CardEntity?, urlEntities: Array<UrlEntity>?,
                         mediaEntities: Array<MediaEntity>?, extendedMediaEntities: Array<MediaEntity>?,
                         accountKey: UserKey, accountType: String): Array<ParcelableMedia> {
        if (card == null) return emptyArray()
        when (card.name) {
            "animated_gif", "player" -> {
                val media = ParcelableMedia()
                val playerStreamUrl = card.getBindingValue("player_stream_url")
                media.card = card.toParcelable(accountKey, accountType)
                val appUrlResolved = card.getBindingValue("app_url_resolved") as? CardEntity.StringValue
                media.url = appUrlResolved?.takeIf { it.checkUrl() }?.value ?: card.url
                if (playerStreamUrl is CardEntity.StringValue) {
                    media.media_url = playerStreamUrl.value
                    if ("animated_gif" == card.name) {
                        media.type = ParcelableMedia.Type.CARD_ANIMATED_GIF
                    } else {
                        media.type = ParcelableMedia.Type.VIDEO
                    }
                } else {
                    val playerUrl = card.getBindingValue("player_url") as? CardEntity.StringValue
                    if (playerUrl != null) {
                        media.media_url = playerUrl.value
                    }
                    media.type = ParcelableMedia.Type.EXTERNAL_PLAYER
                }
                val playerImage = card.getBindingValue("player_image")
                if (playerImage is CardEntity.ImageValue) {
                    media.preview_url = playerImage.url
                    media.width = playerImage.width
                    media.height = playerImage.height
                }
                val playerWidth = card.getBindingValue("player_width")
                val playerHeight = card.getBindingValue("player_height")
                if (playerWidth is CardEntity.StringValue && playerHeight is CardEntity.StringValue) {
                    media.width = playerWidth.value.toIntOr(-1)
                    media.height = playerHeight.value.toIntOr(-1)
                }
                writeLinkInfo(media, urlEntities, mediaEntities, extendedMediaEntities)
                return arrayOf(media)
            }
            "summary_large_image" -> {
                val photoImageFullSize = card.getBindingValue("photo_image_full_size") as? CardEntity.ImageValue ?: return emptyArray()

                val media = ParcelableMedia()
                media.url = card.url
                media.card = card.toParcelable(accountKey, accountType)
                media.type = ParcelableMedia.Type.IMAGE
                media.media_url = photoImageFullSize.url
                media.width = photoImageFullSize.width
                media.height = photoImageFullSize.height
                media.open_browser = true
                val summaryPhotoImage = card.getBindingValue("summary_photo_image")
                if (summaryPhotoImage is CardEntity.ImageValue) {
                    media.preview_url = summaryPhotoImage.url
                }
                return arrayOf(media)
            }
            else -> return emptyArray()
        }
    }

    private fun writeLinkInfo(media: ParcelableMedia, vararg entities: Array<out UrlEntity>?) {
        entities.forEach { array ->
            if (array == null) return@forEach
            for (entity in array) {
                if (entity.url == media.url) {
                    media.page_url = entity.expandedUrl
                    if (media.page_url == null) {
                        media.page_url = media.url
                    }
                    break
                }
            }
        }
    }

    @JvmStatic
    private fun CardEntity.StringValue.checkUrl(): Boolean {
        val value = this.value ?: return false
        return value.startsWith("http://") || value.startsWith("https://")
    }

    fun getTypeInt(type: String): Int {
        when (type) {
            MediaEntity.Type.PHOTO -> return ParcelableMedia.Type.IMAGE
            MediaEntity.Type.VIDEO -> return ParcelableMedia.Type.VIDEO
            MediaEntity.Type.ANIMATED_GIF -> return ParcelableMedia.Type.ANIMATED_GIF
        }
        return ParcelableMedia.Type.UNKNOWN
    }

    fun image(url: String): ParcelableMedia {
        val media = ParcelableMedia()
        media.type = ParcelableMedia.Type.IMAGE
        media.url = url
        media.media_url = url
        media.preview_url = url
        return media
    }

    fun hasPlayIcon(@ParcelableMedia.Type type: Int): Boolean {
        when (type) {
            ParcelableMedia.Type.VIDEO, ParcelableMedia.Type.ANIMATED_GIF,
            ParcelableMedia.Type.CARD_ANIMATED_GIF, ParcelableMedia.Type.EXTERNAL_PLAYER -> return true
            else -> return false
        }
    }

    fun findByUrl(media: Array<ParcelableMedia>?, url: String?): ParcelableMedia? {
        if (media == null || url == null) return null
        for (item in media) {
            if (url == item.url) return item
        }
        return null
    }

    fun getPrimaryMedia(status: ParcelableStatus): Array<ParcelableMedia>? {
        if (status.is_quote && status.media.isNullOrEmpty()) {
            return status.quoted_media
        } else {
            return status.media
        }
    }

    fun getAllMedia(status: ParcelableStatus): Array<ParcelableMedia> {
        val result = ArrayList<ParcelableMedia>()
        status.media?.addAllTo(result)
        status.quoted_media?.addAllTo(result)
        return result.toTypedArray()
    }
}