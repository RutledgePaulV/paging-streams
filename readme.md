[![Build Status](https://travis-ci.org/RutledgePaulV/paging-spliterator.svg?branch=develop)](https://travis-ci.org/RutledgePaulV/paging-spliterator)
[![Coverage Status](https://coveralls.io/repos/github/RutledgePaulV/paging-spliterator/badge.svg?branch=develop)](https://coveralls.io/github/RutledgePaulV/paging-spliterator?branch=develop)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.rutledgepaulv/paging-spliterator/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.rutledgepaulv/paging-spliterator)


### Paging-Spliterator

Spliterators are the Java8 mechanism for detailing how to break items
up into forks of a fork-join tree and how to iterate elements of a single fork.
Due to the convenience of the streams there's a relatively common question of
how to get stream handles from a database without requiring support directly at
the driver level. Since almost all database drivers support requesting pages of
results, it's natural to want a transform between the ability to request pages
to the ability to get a stream of results. That transform is what this library provides.



### Origin
I asked the original question, but absolutely all credit for the implementation goes to
[@holger and his excellent stackoverflow answer](http://stackoverflow.com/a/38312143/2103383).


### Usage

Example of using the spliterator against a faked source (a list with slow fetches).

```Java
    @Test
    public void run() {


        List<Integer> nums = Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .collect(Collectors.toList());

        Stream<Integer> stream = getPagedStream(5, nums);

        stream.forEach(System.out::print);
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

```


### Concurrency Implications
There are two levels of concurrency at play in the fork join tree produced by this spliterator.
The first is in regards to fetching each page, and the second is in regards to processing
the results of each page.

##### Page Fetching
Since the total number of results is only known after requesting the first page,
the parallelism cannot begin until after the first page. For this reason, in order
to see any performance improvement you'll need to be dealing with at least 3 pages.
This is because the first page would be to get the total number of results, and then
the other two pages can be fetched in parallel. If you have less than 3 pages you might
want to consider the tradeoff of context switching to use the fork join pool vs just
using a non-parallel stream.

##### Result Processing
The result parallelism characteristics are really equivalent to processing
a stream originating from a list.

Release Versions
```xml
<dependencies>
    <dependency>
        <groupId>com.github.rutledgepaulv</groupId>
        <artifactId>paging-spliterator</artifactId>
        <version><!-- Not yet released --></version>
    </dependency>
</dependencies>
```

Snapshot Versions
```xml
<dependencies>
    <dependency>
        <groupId>com.github.rutledgepaulv</groupId>
        <artifactId>paging-spliterator</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>ossrh</id>
        <name>Repository for snapshots</name>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```


This project is licensed under [MIT license](http://opensource.org/licenses/MIT).
