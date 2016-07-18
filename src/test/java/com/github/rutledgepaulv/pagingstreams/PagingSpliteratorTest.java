package com.github.rutledgepaulv.pagingstreams;

import com.github.rutledgepaulv.pagingstreams.PageSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import java.util.function.LongConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.rutledgepaulv.pagingstreams.PagingStreams.streamBuilder;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class PagingSpliteratorTest {


    @Test
    public void aSynchronousPagedSourceProcessesAllResultsUnlessPageSizeIsZero() {
        assertEquals(9, synchronous(4, strings(0, 9)).count());
        assertEquals(4, synchronous(4, strings(0, 4)).count());
        assertEquals(0, synchronous(0, strings(0, 4)).count());
    }

    @Test
    public void aParallelPagedSourceProcessesAllResultsUnlessPageSizeIsZero() {
        assertEquals(9, parallel(4, strings(0, 9)).count());
        assertEquals(4, parallel(4, strings(0, 4)).count());
        assertEquals(0, parallel(0, strings(0, 4)).count());
    }

    @Test
    public void aSynchronousPagedSourceMaintainsOrderWhenCollectingToOrderedStructures() {
        assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8]", synchronous(2, strings(0, 9)).collect(toList()).toString());
    }

    @Test
    public void aParallelPagedSourceMaintainsOrderWhenCollectingToOrderedStructures() {
        assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8]", parallel(2, strings(0, 9)).collect(toList()).toString());
    }

    @Test
    public void aSynchronousForEachTraversalOfAPagedSourceMaintainsOrder() {
        List<String> each = new LinkedList<>();
        synchronous(2, strings(0, 9)).forEach(each::add);
        assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8]", each.toString());
    }

    @Test
    public void aParallelForEachTraversalOfAPagedSourceDoesNotMaintainOrder() {
        List<String> each = new CopyOnWriteArrayList<>();
        parallel(2, strings(0, 9)).forEach(each::add);
        assertNotEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8]", each.toString());
    }

    @Test
    public void aParallelForEachOrderedTraversalOfAPagedSourceDoesMaintainOrder() {
        List<String> each = new CopyOnWriteArrayList<>();
        parallel(2, strings(0, 9)).forEachOrdered(each::add);
        assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8]", each.toString());
    }

    @Test
    public void synchronousSourceHasDwindlingResults() {
        assertEquals("[0, 1, 2, 3, 4, 5]", dwindlingSynchronous(2, strings(0, 9)).collect(toList()).toString());
    }

    @Test
    public void parallelSourceWithDwindlingResultsCompletes() {
        assertFalse(dwindlingParallel(2, strings(0, 9)).collect(toList()).isEmpty());
    }

    @Test
    public void synchronousSourceWithGrowingResultsStopsAtCap() {
        assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8]",
                growingSynchronous(2, strings(0, 9)).collect(toList()).toString());
    }

    @Test
    public void parallelSourceWithGrowingSourceStopsAtCap() {
        assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8]", growingParallel(2, strings(0, 9)).collect(toList()).toString());
    }

    private static List<String> strings(int start, int end) {
        return IntStream.range(start, end).boxed().map(Objects::toString).collect(toList());
    }

    private static <T> Stream<T> parallel(long pageSize, List<T> items) {
        return streamBuilder(getSource(items)).pageSize(pageSize).parallel(true).build();
    }

    private static <T> Stream<T> synchronous(long pageSize, List<T> items) {
        return streamBuilder(getSource(items)).pageSize(pageSize).parallel(false).build();
    }

    private static <T> Stream<T> dwindlingParallel(long pageSize, List<T> items) {
        return streamBuilder(getDwindlingSource(items)).pageSize(pageSize).parallel(true).build();
    }

    private static <T> Stream<T> dwindlingSynchronous(long pageSize, List<T> items) {
        return streamBuilder(getDwindlingSource(items)).pageSize(pageSize).parallel(false).build();
    }

    private static <T> Stream<T> growingParallel(long pageSize, List<T> items) {
        return streamBuilder(getGrowingSource(items)).pageSize(pageSize).parallel(true).build();
    }

    private static <T> Stream<T> growingSynchronous(long pageSize, List<T> items) {
        return streamBuilder(getGrowingSource(items)).pageSize(pageSize).parallel(false).build();
    }

    private static <T> PageSource<T> getSource(List<T> items) {
        return (offset, limit, totalSizeSink) -> {
            sleep();
            totalSizeSink.accept(items.size());
            return items.subList((int) offset, min((int) (offset + limit), items.size()));
        };
    }

    private static <T> PageSource<T> getDwindlingSource(List<T> items) {
        final AtomicInteger total = new AtomicInteger(items.size());
        return new PageSource<T>() {
            @Override
            public List<T> fetch(long offset, long limit, LongConsumer totalSizeSink) {
                sleep();
                synchronized(this) {
                    totalSizeSink.accept(total.getAndDecrement());
                    return items.subList((int) offset, (int) max(offset, min((int) (offset + limit), total.get())));
                }
            }
        };
    }

    private static <T> PageSource<T> getGrowingSource(List<T> items) {
        final AtomicInteger incrementer = new AtomicInteger(items.size());
        final IntUnaryOperator op = i -> i + (incrementer.getAndDecrement() > 0 ? incrementer.get() : 0);
        final AtomicInteger total = new AtomicInteger(items.size());
        return new PageSource<T>() {
            @Override
            public List<T> fetch(long offset, long limit, LongConsumer totalSizeSink) {
                sleep();
                synchronized(this) {
                    long previousTotal = total.get();
                    totalSizeSink.accept(previousTotal);
                    items.addAll(new ArrayList<>(items.subList(0, total.updateAndGet(op) - (int) previousTotal)));
                    return new ArrayList<>(items.subList((int) offset, (int) Math.min(offset + limit, items.size())));
                }
            }
        };
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        }catch(InterruptedException ignored) {}
    }

}