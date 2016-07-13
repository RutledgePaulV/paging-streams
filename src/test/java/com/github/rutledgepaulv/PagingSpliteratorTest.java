package com.github.rutledgepaulv;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PagingSpliteratorTest {


    @Test
    public void run() {


        List<Integer> nums = Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .collect(Collectors.toList());

        Stream<Integer> stream = getPagedStream(5, nums);

        stream.forEachOrdered(System.out::print);
    }


    private static <T> Stream<T> getPagedStream(long pageSize, List<T> items) {

        PageProvider<T> producer = (offset, limit, totalSizeSink) -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}

            int endIndex = Math.min((int) (offset + limit), items.size());
            totalSizeSink.accept(items.size());
            return items.subList((int) offset, endIndex);
        };

        return PagingSpliterator.streamPagedSource(producer, pageSize, true);
    }

}