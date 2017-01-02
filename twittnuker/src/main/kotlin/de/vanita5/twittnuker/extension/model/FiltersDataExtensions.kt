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

package de.vanita5.twittnuker.extension.model

import android.content.ContentResolver
import android.net.Uri
import org.mariotaku.ktextension.addAllEnhanced
import org.mariotaku.ktextension.convert
import org.mariotaku.ktextension.map
import org.mariotaku.sqliteqb.library.Expression
import de.vanita5.twittnuker.model.*
import de.vanita5.twittnuker.provider.TwidereDataStore.Filters
import de.vanita5.twittnuker.util.content.ContentResolverUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.util.*

fun FiltersData.read(cr: ContentResolver, loadSubscription: Boolean = false) {
    fun readBaseItems(uri: Uri): List<FiltersData.BaseItem>? {
        val where = if (loadSubscription) null else Expression.lesserThan(Filters.SOURCE, 0).sql
        val c = cr.query(uri, Filters.COLUMNS, where, null, null) ?: return null
        @Suppress("ConvertTryFinallyToUseCall")
        try {
            return c.map(`FiltersData$BaseItemCursorIndices`(c))
        } finally {
            c.close()
        }
    }
    this.users = run {
        val where = if (loadSubscription) null else Expression.lesserThan(Filters.Users.SOURCE, 0).sql
        val c = cr.query(Filters.Users.CONTENT_URI, Filters.Users.COLUMNS, where, null, null) ?: return@run null
        @Suppress("ConvertTryFinallyToUseCall")
        try {
            return@run c.map(`FiltersData$UserItemCursorIndices`(c))
        } finally {
            c.close()
        }
    }
    this.keywords = readBaseItems(Filters.Keywords.CONTENT_URI)
    this.sources = readBaseItems(Filters.Sources.CONTENT_URI)
    this.links = readBaseItems(Filters.Links.CONTENT_URI)
}

fun FiltersData.write(cr: ContentResolver) {
    if (users != null) {
        ContentResolverUtils.bulkInsert(cr, Filters.Users.CONTENT_URI,
                users.map(`FiltersData$UserItemValuesCreator`::create))
    }
    if (keywords != null) {
        ContentResolverUtils.bulkInsert(cr, Filters.Keywords.CONTENT_URI,
                keywords.map(`FiltersData$BaseItemValuesCreator`::create))
    }
    if (sources != null) {
        ContentResolverUtils.bulkInsert(cr, Filters.Sources.CONTENT_URI,
                sources.map(`FiltersData$BaseItemValuesCreator`::create))
    }
    if (links != null) {
        ContentResolverUtils.bulkInsert(cr, Filters.Links.CONTENT_URI,
                links.map(`FiltersData$BaseItemValuesCreator`::create))
    }
}

private const val TAG_FILTERS = "filters"
private const val TAG_KEYWORD = "keyword"
private const val TAG_SOURCE = "source"
private const val TAG_LINK = "link"
private const val TAG_USER = "user"

private const val ATTR_SCREEN_NAME = "screenName"
private const val ATTR_NAME = "name"
private const val ATTR_KEY = "key"


@Throws(IOException::class)
fun FiltersData.serialize(serializer: XmlSerializer) {

    @Throws(IOException::class)
    fun FiltersData.BaseItem.serialize(name: String, writer: XmlSerializer) {
        writer.startTag(null, name)
        writer.text(value)
        writer.endTag(null, name)
    }

    serializer.startDocument("utf-8", true)
    serializer.startTag(null, TAG_FILTERS)
    this.users?.forEach { user ->
        serializer.startTag(null, TAG_USER)
        serializer.attribute(null, ATTR_KEY, user.userKey.toString())
        serializer.attribute(null, ATTR_NAME, user.name)
        serializer.attribute(null, ATTR_SCREEN_NAME, user.screenName)
        serializer.endTag(null, TAG_USER)
    }
    this.keywords?.forEach { it.serialize(TAG_KEYWORD, serializer) }
    this.sources?.forEach { it.serialize(TAG_SOURCE, serializer) }
    this.links?.forEach { it.serialize(TAG_LINK, serializer) }
    serializer.endTag(null, TAG_FILTERS)
    serializer.endDocument()
}

@Throws(IOException::class)
fun FiltersData.parse(parser: XmlPullParser) {
    fun parseUserItem(parser: XmlPullParser): FiltersData.UserItem? {
        val item = FiltersData.UserItem()
        item.name = parser.getAttributeValue(null, ATTR_NAME) ?: return null
        item.screenName = parser.getAttributeValue(null, ATTR_SCREEN_NAME) ?: return null
        item.userKey = parser.getAttributeValue(null, ATTR_KEY)?.convert(UserKey::valueOf) ?: return null
        return item
    }

    var event = parser.eventType
    var stack = Stack<Any?>()
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_DOCUMENT -> {
                initFields()
            }
            XmlPullParser.START_TAG -> {
                stack.push(when (parser.name) {
                    TAG_USER -> parseUserItem(parser)
                    TAG_KEYWORD, TAG_SOURCE, TAG_LINK -> FiltersData.BaseItem()
                    else -> null
                })
            }
            XmlPullParser.END_TAG -> {
                val obj = stack.pop()
                when (parser.name) {
                    TAG_USER -> (obj as? FiltersData.UserItem).let { users.add(it) }
                    TAG_KEYWORD -> (obj as? FiltersData.BaseItem).let { keywords.add(it) }
                    TAG_SOURCE -> (obj as? FiltersData.BaseItem).let { sources.add(it) }
                    TAG_LINK -> (obj as? FiltersData.BaseItem).let { links.add(it) }
                }
            }
            XmlPullParser.TEXT -> {
                stack.push(run {
                    val obj = stack.pop()
                    when (obj) {
                        is FiltersData.BaseItem -> {
                            obj.value = parser.text ?: return@run null
                        }
                    }
                    return@run obj
                })
            }
        }
        event = parser.next()
    }

}

fun FiltersData.addAll(data: FiltersData, ignoreDuplicates: Boolean = false): Boolean {
    var changed: Boolean = false
    if (this.users != null) {
        changed = changed or this.users.addAllEnhanced(collection = data.users, ignoreDuplicates = ignoreDuplicates)
        } else {
        this.users = data.users
        changed = true
        }
    if (this.keywords != null) {
        changed = changed or this.keywords.addAllEnhanced(collection = data.keywords, ignoreDuplicates = ignoreDuplicates)
    } else {
        this.keywords = data.keywords
        changed = true
    }
    if (this.sources != null) {
        changed = changed or this.sources.addAllEnhanced(collection = data.sources, ignoreDuplicates = ignoreDuplicates)
    } else {
        this.sources = data.sources
        changed = true
    }
    if (this.links != null) {
        changed = changed or this.links.addAllEnhanced(collection = data.links, ignoreDuplicates = ignoreDuplicates)
    } else {
        this.links = data.links
        changed = true
    }
    return changed
}

fun FiltersData.removeAll(data: FiltersData): Boolean {
    var changed: Boolean = false
    changed = changed or (data.users?.let { this.users?.removeAll(it) } ?: false)
    changed = changed or (data.keywords?.let { this.keywords?.removeAll(it) } ?: false)
    changed = changed or (data.sources?.let { this.sources?.removeAll(it) } ?: false)
    changed = changed or (data.links?.let { this.links?.removeAll(it) } ?: false)
    return changed
}

fun FiltersData.initFields() {
    if (users == null) {
        users = ArrayList()
    }
    if (keywords == null) {
        keywords = ArrayList()
    }
    if (sources == null) {
        sources = ArrayList()
    }
    if (links == null) {
        links = ArrayList()
    }
}