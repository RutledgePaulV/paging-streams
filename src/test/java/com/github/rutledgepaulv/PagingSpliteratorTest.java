package com.github.rutledgepaulv;

import org.junit.Test;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.min;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertTrue;

public class PagingSpliteratorTest {


    @Test
    public void test() {

        List<String> items = IntStream.range(0, 200).boxed()
                .map(Object::toString).collect(toList());

        Stream<String> stream = PagingStreamSupport
                .streamBuilder(getProvider(items))
                .pageSize(30).parallel(true).build();


        long start = time();
        stream.forEach(item -> {});
        long stop = time();

        assertTrue(stop - start < 5000);
        assertTrue(stop - start > 3000);
    }


    private long time() {
        return System.currentTimeMillis();
    }

    private static PageSource<String> getProvider(List<String> items) {
        return (offset, limit, totalSizeSink) -> {
            sleep(2000);
            totalSizeSink.accept(items.size());
            return items.subList((int) offset, min((int) (offset + limit), items.size()));
        };
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException ignored) {}
    }

}