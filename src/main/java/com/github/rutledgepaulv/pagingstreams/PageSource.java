package com.github.rutledgepaulv.pagingstreams;

import java.util.List;
import java.util.function.LongConsumer;

@SuppressWarnings("WeakerAccess")
@FunctionalInterface
public interface PageSource<T> {

    /**
     * Get a single page of results given an offset and limit. Should also set
     * the expected number of total results.
     *
     * @param offset The offset at which to begin taking elements (zero indexed)
     * @param limit The maximum number of elements that should be taken beginning at the offset.
     *
     * @param totalSizeSink A consumer to set the total size onto after querying the page.
     *
     * @return The page of results as a list.
     */
    List<T> fetch(long offset, long limit, LongConsumer totalSizeSink);

}
