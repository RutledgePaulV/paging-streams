package com.github.rutledgepaulv;

import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterator.*;

/**
 * Provides transforms between paged sources to spliterators and streams.
 */
public final class PagingStreamSupport {
    private PagingStreamSupport(){}
    private static final long DEFAULT_PAGE_SIZE = 100;
    private static final int CHARACTERISTICS = IMMUTABLE|ORDERED|SIZED|SUBSIZED;

    /**
     * Gets a builder for constructing a stream from a paged source.
     *
     * @param source The paged source.
     * @param <T> The type contained within each page / the resulting stream.
     * @return The stream builder.
     */
    public static <T> StreamBuilder<T> streamBuilder(PageSource<T> source) {
        return new StreamBuilder<>(source);
    }

    /**
     * Gets a builder for constructing a spliterator from a paged source.
     *
     * @param source The paged source.
     * @param <T> The type contained within each page / the resulting spliterator.
     * @return The spliterator builder.
     */
    public static <T> SpliteratorBuilder<T> spliteratorBuilder(PageSource<T> source) {
        return new SpliteratorBuilder<>(source);
    }




    public static final class StreamBuilder<T> {
        private StreamBuilder(PageSource<T> source){
            this.source = Objects.requireNonNull(source);
        }

        private long pageSize = DEFAULT_PAGE_SIZE;
        private PageSource<T> source;
        private boolean parallel;

        public StreamBuilder<T> pageSize(long pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public StreamBuilder<T> parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        public Stream<T> build() {
            return StreamSupport.stream(PagingStreamSupport.spliteratorBuilder(source)
                    .pageSize(pageSize)::build, CHARACTERISTICS, parallel);
        }

    }

    public static final class SpliteratorBuilder<T> {
        private SpliteratorBuilder(PageSource<T> source){
            this.source = Objects.requireNonNull(source);
        }

        private long pageSize = DEFAULT_PAGE_SIZE;
        private PageSource<T> source;

        public SpliteratorBuilder<T> pageSize(long pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Spliterator<T> build() {
            if(this.pageSize == 0) return Stream.<T>empty().spliterator();
            PagingSpliterator<T> pgSp = new PagingSpliterator<>(this.source, 0, 0, this.pageSize);
            pgSp.danglingFirstPage = spliterator(this.source.fetch(0, this.pageSize, l -> pgSp.end=l));
            return pgSp;
        }

    }



    private static final class PagingSpliterator<T> implements Spliterator<T> {

        private long start, end, pageSize;
        private Spliterator<T> currentPage;
        private final PageSource<T> supplier;
        private Spliterator<T> danglingFirstPage;


        private PagingSpliterator(PageSource<T> supplier, long start, long end, long pageSize) {
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
                sp = spliterator(supplier.fetch(start, Math.min(end-start, pageSize), l->{}));
                start += sp.getExactSizeIfKnown();
                currentPage=sp;
            }
            return sp;
        }

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
