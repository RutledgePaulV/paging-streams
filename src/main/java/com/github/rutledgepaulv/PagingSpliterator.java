package com.github.rutledgepaulv;

import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SuppressWarnings({"unchecked", "WeakerAccess"})
public final class PagingSpliterator<T> implements Spliterator<T> {

    private static final int CHARACTERISTICS = IMMUTABLE|ORDERED|SIZED|SUBSIZED;


    public static <T> Stream<T> streamPagedSource(PageProvider<T> pageProvider, long pageSize) {
        return streamPagedSource(pageProvider, pageSize, false);
    }


    public static <T> Stream<T> streamPagedSource(PageProvider<T> pageProvider, long pageSize, boolean parallel) {
        if(pageSize<=0) return Stream.empty();
        return StreamSupport.stream(() -> {
            PagingSpliterator<T> pgSp = new PagingSpliterator<>(pageProvider, 0, 0, pageSize);
            pgSp.danglingFirstPage = spliterator(pageProvider.fetchPage(0, pageSize, l -> pgSp.end=l));
            return pgSp;
        }, CHARACTERISTICS, parallel);
    }


    private final PageProvider<T> supplier;
    private long start, end, pageSize;
    private Spliterator<T> currentPage, danglingFirstPage;

    private PagingSpliterator(PageProvider<T> supplier, long start, long end, long pageSize) {
        this.supplier = supplier;
        this.start    = start;
        this.end      = end;
        this.pageSize = pageSize;
    }

    @Override
    public final boolean tryAdvance(Consumer<? super T> action) {
        for(;;) {
            if(ensurePage().tryAdvance(action)) return true;
            if(start>=end) return false;
            currentPage=null;
        }
    }

    @Override
    public final void forEachRemaining(Consumer<? super T> action) {
        do {
            ensurePage().forEachRemaining(action);
            currentPage=null;
        } while(start<end);
    }

    @Override
    public final Spliterator<T> trySplit() {
        if(danglingFirstPage!=null) {
            Spliterator<T> fp=danglingFirstPage;
            danglingFirstPage=null;
            start=fp.getExactSizeIfKnown();
            return fp;
        }
        if(currentPage!=null)
            return currentPage.trySplit();
        if(end-start>pageSize) {
            long mid=(start+end)>>>1;
            mid=mid/pageSize*pageSize;
            if(mid==start) mid+=pageSize;
            return new PagingSpliterator<T>(supplier, start, start=mid, pageSize);
        }
        return ensurePage().trySplit();
    }

    @Override
    public final long estimateSize() {
        if(currentPage!=null) return currentPage.estimateSize();
        return end-start;
    }

    @Override
    public final int characteristics() {
        return CHARACTERISTICS;
    }

    /**
     * Fetch data immediately before traversing or sub-page splitting.
     */
    private Spliterator<T> ensurePage() {
        if(danglingFirstPage!=null) {
            Spliterator<T> fp=danglingFirstPage;
            danglingFirstPage=null;
            currentPage=fp;
            start=fp.getExactSizeIfKnown();
            return fp;
        }
        Spliterator<T> sp = currentPage;
        if(sp==null) {
            if(start>=end) return Spliterators.emptySpliterator();
            sp = spliterator(supplier.fetchPage(start, Math.min(end-start, pageSize), l->{}));
            start += sp.getExactSizeIfKnown();
            currentPage=sp;
        }
        return sp;
    }


    /**
     * Ensure that the sub-spliterator provided by the List is compatible with
     * ours, i.e. is {@code SIZED | SUBSIZED}. For standard List implementations,
     * the spliterators are, so the costs of dumping into an intermediate array
     * in the other case is irrelevant.
     */
    private static <E> Spliterator<E> spliterator(List<E> list) {
        Spliterator<E> sp = list.spliterator();

        if((sp.characteristics()&(SIZED|SUBSIZED))!=(SIZED|SUBSIZED)) {
            sp = Spliterators.spliterator(StreamSupport.stream(sp, false).toArray(), IMMUTABLE | ORDERED);
        }

        return sp;
    }



}
