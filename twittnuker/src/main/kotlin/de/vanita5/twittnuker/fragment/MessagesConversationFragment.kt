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

package de.vanita5.twittnuker.fragment

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v7.widget.FixedLinearLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.otto.Subscribe
import kotlinx.android.synthetic.main.fragment_messages_conversation.*
import org.mariotaku.abstask.library.TaskStarter
import org.mariotaku.kpreferences.get
import org.mariotaku.ktextension.empty
import org.mariotaku.pickncrop.library.MediaPickerActivity
import org.mariotaku.sqliteqb.library.Expression
import org.mariotaku.sqliteqb.library.OrderBy
import de.vanita5.twittnuker.R
import de.vanita5.twittnuker.TwittnukerConstants.REQUEST_PICK_MEDIA
import de.vanita5.twittnuker.activity.ThemedMediaPickerActivity
import de.vanita5.twittnuker.adapter.MediaPreviewAdapter
import de.vanita5.twittnuker.adapter.MessagesConversationAdapter
import de.vanita5.twittnuker.adapter.iface.ILoadMoreSupportAdapter
import de.vanita5.twittnuker.constant.IntentConstants.EXTRA_ACCOUNT_KEY
import de.vanita5.twittnuker.constant.IntentConstants.EXTRA_CONVERSATION_ID
import de.vanita5.twittnuker.constant.newDocumentApiKey
import de.vanita5.twittnuker.loader.ObjectCursorLoader
import de.vanita5.twittnuker.model.*
import de.vanita5.twittnuker.model.ParcelableMessageConversation.ConversationType
import de.vanita5.twittnuker.model.event.GetMessagesTaskEvent
import de.vanita5.twittnuker.model.util.AccountUtils
import de.vanita5.twittnuker.provider.TwidereDataStore.Messages
import de.vanita5.twittnuker.service.LengthyOperationsService
import de.vanita5.twittnuker.task.GetMessagesTask
import de.vanita5.twittnuker.task.compose.AbsAddMediaTask
import de.vanita5.twittnuker.util.DataStoreUtils
import de.vanita5.twittnuker.util.IntentUtils
import de.vanita5.twittnuker.util.PreviewGridItemDecoration
import java.util.concurrent.atomic.AtomicReference

class MessagesConversationFragment : AbsContentListRecyclerViewFragment<MessagesConversationAdapter>(),
        LoaderManager.LoaderCallbacks<List<ParcelableMessage>?> {
    private lateinit var mediaPreviewAdapter: MediaPreviewAdapter

    private val accountKey: UserKey get() = arguments.getParcelable(EXTRA_ACCOUNT_KEY)

    private val conversationId: String get() = arguments.getString(EXTRA_CONVERSATION_ID)

    private val account: AccountDetails? by lazy {
        AccountUtils.getAccountDetails(AccountManager.get(context), accountKey, true)
    }

    private val loadMoreTaskTag: String
        get() = "loadMore:$accountKey:$conversationId"

    // Layout manager reversed, so treat start as end
    override val reachingEnd: Boolean
        get() = super.reachingStart

    // Layout manager reversed, so treat end as start
    override val reachingStart: Boolean
        get() = super.reachingEnd

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        adapter.listener = object : MessagesConversationAdapter.Listener {
            override fun onMediaClick(position: Int, media: ParcelableMedia, accountKey: UserKey?) {
                val message = adapter.getMessage(position) ?: return
                IntentUtils.openMediaDirectly(context = context, accountKey = accountKey,
                        media = message.media, current = media,
                        newDocument = preferences[newDocumentApiKey], message = message)
            }
        }
        mediaPreviewAdapter = MediaPreviewAdapter(context)

        attachedMediaPreview.layoutManager = FixedLinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        attachedMediaPreview.adapter = mediaPreviewAdapter
        attachedMediaPreview.addItemDecoration(PreviewGridItemDecoration(resources.getDimensionPixelSize(R.dimen.element_spacing_small)))

        sendMessage.setOnClickListener {
            performSendMessage()
        }
        addMedia.setOnClickListener {
            openMediaPicker()
        }

        // No refresh for this fragment
        refreshEnabled = false
        adapter.loadMoreSupportedPosition = ILoadMoreSupportAdapter.NONE

        updateMediaPreview()

        loaderManager.initLoader(0, null, this)
        showProgress()
    }

    override fun onStart() {
        super.onStart()
        bus.register(this)
    }

    override fun onStop() {
        bus.unregister(this)
        super.onStop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_PICK_MEDIA -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val mediaUris = MediaPickerActivity.getMediaUris(data)
                    TaskStarter.execute(AddMediaTask(this, mediaUris, true))
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_messages_conversation, container, false)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<ParcelableMessage>?> {
        return ConversationLoader(context, accountKey, conversationId)
    }

    override fun onLoaderReset(loader: Loader<List<ParcelableMessage>?>) {
        adapter.setData(null, null)
    }

    override fun onLoadFinished(loader: Loader<List<ParcelableMessage>?>, data: List<ParcelableMessage>?) {
        val conversationLoader = loader as? ConversationLoader
        val conversation = conversationLoader?.conversation
        adapter.setData(conversation, data)
        adapter.displaySenderProfile = conversation?.conversation_type == ConversationType.GROUP
        if (conversation?.conversation_extras_type == ParcelableMessageConversation.ExtrasType.TWITTER_OFFICIAL) {
            adapter.loadMoreSupportedPosition = ILoadMoreSupportAdapter.START
        } else {
            adapter.loadMoreSupportedPosition = ILoadMoreSupportAdapter.NONE
        }
        showContent()
    }

    override fun onCreateAdapter(context: Context): MessagesConversationAdapter {
        return MessagesConversationAdapter(context)
    }

    override fun onCreateLayoutManager(context: Context): LinearLayoutManager {
        return FixedLinearLayoutManager(context, LinearLayoutManager.VERTICAL, true)
    }

    override fun createItemDecoration(context: Context, recyclerView: RecyclerView,
            layoutManager: LinearLayoutManager): RecyclerView.ItemDecoration? {
        return null
    }

    override fun onLoadMoreContents(position: Long) {
        if (position and ILoadMoreSupportAdapter.START == 0L) return
        val message = adapter.getMessage(adapter.messageRange.endInclusive) ?: return
        setLoadMoreIndicatorPosition(position)
        val param = GetMessagesTask.LoadMoreMessageTaskParam(context, accountKey, conversationId,
                message.id)
        param.taskTag = loadMoreTaskTag
        twitterWrapper.getMessagesAsync(param)
    }

    @Subscribe
    fun onGetMessagesTaskEvent(event: GetMessagesTaskEvent) {
        if (!event.running && event.taskTag == loadMoreTaskTag) {
            setLoadMoreIndicatorPosition(ILoadMoreSupportAdapter.NONE)
        }
    }

    private fun performSendMessage() {
        val conversation = adapter.conversation ?: return
        if (editText.empty && adapter.itemCount == 0) {
            editText.error = getString(R.string.hint_error_message_no_content)
            return
        }
        val text = editText.text.toString()
        val message = ParcelableNewMessage().apply {
            this.account = this@MessagesConversationFragment.account
            this.media = mediaPreviewAdapter.asList().toTypedArray()
            this.conversation_id = conversation.id
            this.recipient_ids = conversation.participants?.map {
                it.key.id
            }?.toTypedArray()
            this.text = text
        }
        LengthyOperationsService.sendMessageAsync(context, message)
        editText.text = null

        // Clear media, those media will be deleted after sent
        mediaPreviewAdapter.clear()
        updateMediaPreview()
    }

    private fun openMediaPicker() {
        val intent = ThemedMediaPickerActivity.withThemed(context)
                .pickSources(arrayOf(MediaPickerActivity.SOURCE_CAMERA,
                        MediaPickerActivity.SOURCE_CAMCORDER,
                        MediaPickerActivity.SOURCE_GALLERY,
                        MediaPickerActivity.SOURCE_CLIPBOARD))
                .containsVideo(true)
                .allowMultiple(false)
                .build()
        startActivityForResult(intent, REQUEST_PICK_MEDIA)
    }

    private fun attachMedia(media: List<ParcelableMediaUpdate>) {
        mediaPreviewAdapter.addAll(media)
        updateMediaPreview()
    }

    private fun updateMediaPreview() {
        attachedMediaPreview.visibility = if (mediaPreviewAdapter.itemCount > 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun setProgressVisible(visible: Boolean) {

    }

    internal class AddMediaTask(
            fragment: MessagesConversationFragment,
            sources: Array<Uri>,
            deleteSrc: Boolean
    ) : AbsAddMediaTask<MessagesConversationFragment>(fragment.context, sources, deleteSrc) {

        init {
            callback = fragment
        }

        override fun afterExecute(callback: MessagesConversationFragment?, result: List<ParcelableMediaUpdate>?) {
            if (callback == null || result == null) return
            callback.setProgressVisible(false)
            callback.attachMedia(result)
        }

        override fun beforeExecute() {
            val fragment = callback ?: return
            fragment.setProgressVisible(true)
        }

    }

    internal class ConversationLoader(
            context: Context,
            val accountKey: UserKey,
            val conversationId: String
    ) : ObjectCursorLoader<ParcelableMessage>(context, ParcelableMessageCursorIndices::class.java) {

        private val atomicConversation = AtomicReference<ParcelableMessageConversation?>()
        val conversation: ParcelableMessageConversation? get() = atomicConversation.get()

        init {
            uri = Messages.CONTENT_URI
            projection = Messages.COLUMNS
            selection = Expression.and(Expression.equalsArgs(Messages.ACCOUNT_KEY),
                    Expression.equalsArgs(Messages.CONVERSATION_ID)).sql
            selectionArgs = arrayOf(accountKey.toString(), conversationId)
            sortOrder = OrderBy(Messages.SORT_ID, false).sql
        }

        override fun onLoadInBackground(): MutableList<ParcelableMessage> {
            atomicConversation.set(DataStoreUtils.findMessageConversation(context, accountKey, conversationId))
            return super.onLoadInBackground()
        }
    }

}
