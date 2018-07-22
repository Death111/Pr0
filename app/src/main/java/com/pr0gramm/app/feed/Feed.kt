package com.pr0gramm.app.feed

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.parcel.creator
import com.pr0gramm.app.parcel.readTyped
import com.pr0gramm.app.parcel.readTypedList
import com.pr0gramm.app.parcel.writeTyped
import com.pr0gramm.app.util.toInt

/**
 * Represents a feed.
 */
data class Feed(val filter: FeedFilter = FeedFilter(),
                val contentType: Set<ContentType> = setOf(ContentType.SFW),
                val items: List<FeedItem> = listOf(),
                val isAtEnd: Boolean = false,
                val isAtStart: Boolean = false,
                val created: Instant = Instant.now()) : List<FeedItem> by items {


    private val itemComparator = compareByDescending(this::feedTypeId)

    val feedType: FeedType get() = filter.feedType
    val oldest: FeedItem? get() = items.maxWith(itemComparator)
    val newest: FeedItem? get() = items.minWith(itemComparator)

    /**
     * Merges this feed with the provided low level feed representation
     * and returns a new, immutable merged feed.
     */
    fun mergeWith(feed: Api.Feed): Feed {
        val isAtEnd = isAtEnd or feed.isAtEnd
        val isAtStart = isAtStart or feed.isAtStart or !feedType.sortable

        val newItems = mergeItems(feed.items)
        return copy(items = newItems, isAtStart = isAtStart, isAtEnd = isAtEnd)
    }

    /**
     * Adds the api feed to the items of this feed and returns
     * a list of items.
     */
    private fun mergeItems(feedItems: List<Api.Feed.Item>): List<FeedItem> {
        val newItems = feedItems.mapTo(mutableListOf(), ::FeedItem)

        // merge them in the correct order.
        val target = ArrayList<FeedItem>(items.size + newItems.size)

        if (feedType.sortable) {
            // get two sorted iterators.
            val old = PeekingIterator(items.sortedWith(itemComparator).iterator())
            val new = PeekingIterator(newItems.sortedWith(itemComparator).iterator())

            while (new.hasNext() && old.hasNext()) {
                val cmp = itemComparator.compare(new.peek(), old.peek())
                if (cmp > 0) {
                    target.add(old.next())
                } else {
                    target.add(new.next())
                }
            }

            // just add the rest
            new.forEach { target.add(it) }
            old.forEach { target.add(it) }
        } else {
            target += this
            target += newItems
        }

        // verify that we did not at an item twice
        return target.distinctBy { it.id }
    }

    fun feedTypeId(item: FeedItem): Long {
        return item.id(feedType)
    }

    fun indexById(itemId: Long): Int? {
        val index = items.indexOfFirst { it.id == itemId }
        return if (index >= 0) index else null
    }

    override fun toString(): String {
        return "Feed[newest=${newest?.id}, oldest=${oldest?.id}, size=$size]"
    }

    /**
     * Implementation of PeekingIterator that avoids peeking unless necessary.
     */
    private class PeekingIterator<out E>(private val iterator: Iterator<E>) : Iterator<E> {
        private var hasPeeked: Boolean = false
        private var peekedElement: E? = null

        override fun hasNext(): Boolean {
            return hasPeeked || iterator.hasNext()
        }

        override fun next(): E {
            if (!hasPeeked) {
                return iterator.next()
            }
            val result = peekedElement!!
            hasPeeked = false
            peekedElement = null
            return result
        }

        fun peek(): E? {
            if (!hasPeeked) {
                peekedElement = iterator.next()
                hasPeeked = true
            }

            return peekedElement
        }
    }

    fun parcelAround(pivot: Int): FeedParcel {
        // how many items to save to older and newer than the pivot item.
        val itemCountAround = 64

        // add a subset of the items
        val start = (pivot - itemCountAround).coerceIn(0, items.size)
        val stop = (pivot + itemCountAround).coerceIn(0, items.size)

        return FeedParcel(copy(
                isAtStart = isAtStart && start == 0,
                isAtEnd = false,
                items = this.items.subList(start, stop).toList()))
    }

    class FeedParcel(val feed: Feed) : Parcelable {
        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
            writeTyped(feed.filter)
            writeInt(ContentType.combine(feed.contentType))
            writeInt(feed.isAtStart.toInt())
            writeTyped(feed.created)
            writeTypedList(feed.items)
        }

        companion object {
            @JvmField
            val CREATOR = creator { parcel ->
                FeedParcel(Feed(
                        filter = parcel.readTyped(FeedFilter.CREATOR),
                        contentType = ContentType.decompose(parcel.readInt()),
                        isAtStart = parcel.readInt() != 0,
                        created = parcel.readTyped(Instant.CREATOR),
                        items = parcel.readTypedList(FeedItem.CREATOR)))
            }
        }
    }
}
