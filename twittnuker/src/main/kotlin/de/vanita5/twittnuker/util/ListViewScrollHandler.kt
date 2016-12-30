package de.vanita5.twittnuker.util

import android.widget.AbsListView

import de.vanita5.twittnuker.util.support.ViewSupport

class ListViewScrollHandler(
        contentListSupport: ContentScrollHandler.ContentListSupport,
        viewCallback: ContentScrollHandler.ViewCallback?
) : ContentScrollHandler(contentListSupport, viewCallback), AbsListView.OnScrollListener,
        ListScrollDistanceCalculator.ScrollDistanceListener {
    private val calculator: ListScrollDistanceCalculator
    var onScrollListener: AbsListView.OnScrollListener? = null
    private var dy: Int = 0
    private var oldState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE

    init {
        calculator = ListScrollDistanceCalculator()
    }

    override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
        calculator.onScrollStateChanged(view, scrollState)
        calculator.setScrollDistanceListener(this)
        handleScrollStateChanged(scrollState, AbsListView.OnScrollListener.SCROLL_STATE_IDLE)
        if (onScrollListener != null) {
            onScrollListener!!.onScrollStateChanged(view, scrollState)
        }
    }

    override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        calculator.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount)
        val scrollState = scrollState
        handleScroll(dy, scrollState, oldState, AbsListView.OnScrollListener.SCROLL_STATE_IDLE)
        if (onScrollListener != null) {
            onScrollListener!!.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount)
        }
    }

    val totalScrollDistance: Int
        get() = calculator.totalScrollDistance

    override fun onScrollDistanceChanged(delta: Int, total: Int) {
        dy = -delta
        val scrollState = scrollState
        handleScroll(dy, scrollState, oldState, AbsListView.OnScrollListener.SCROLL_STATE_IDLE)
        oldState = scrollState
    }

    class ListViewCallback(private val listView: AbsListView) : ContentScrollHandler.ViewCallback {

        override val computingLayout: Boolean
            get() = ViewSupport.isInLayout(listView)

        override fun post(runnable: Runnable) {
            listView.post(runnable)
        }
    }
}