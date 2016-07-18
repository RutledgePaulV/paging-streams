[![Build Status](https://travis-ci.org/RutledgePaulV/paging-streams.svg?branch=develop)](https://travis-ci.org/RutledgePaulV/paging-streams)
[![Coverage Status](https://coveralls.io/repos/github/RutledgePaulV/paging-streams/badge.svg?branch=develop)](https://coveralls.io/github/RutledgePaulV/paging-streams?branch=develop)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.rutledgepaulv/paging-streams/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.rutledgepaulv/paging-streams)


### Paging-Streams

Spliterators are the Java8 mechanisms for detailing how to get and traverse elements
of a stream view over some source. Spliterators include details like how to break 
processes across elements up into forks of a fork-join tree and how to iterate 
elements of a single fork. Due to the convenience of the streams there is a relatively 
common question of how to get stream handles from a database without requiring support 
directly at the driver level. Since almost all database drivers support requesting pages 
of results, it's natural to want a transform between the ability to request pages
to the ability to get a stream of results. That transform is what this library provides.



### Origin
I asked the original question, but absolutely all credit for the implementation goes to
[@holger and his excellent stackoverflow answer](http://stackoverflow.com/a/38312143/2103383).


### Usage

Example of using the spliterator against a faked source (a list with slow fetches).

```Java

    @Test
    public void test() {


        List<String> items = IntStream.range(0, 200).boxed()
                .map(Object::toString).collect(toList());

        Stream<String> stream = PagingStreams
                .streamBuilder(getProvider(items))
                .pageSize(30).parallel(true).build();


        long start = time();
        stream.forEach(item -> {});
        long stop = time();


         // completes in about 4 seconds on a macbook pro
         // even though requesting each of the pages sequentially
         // would take 12 seconds
        assertTrue(stop - start < 5000);
        assertTrue(stop - start > 3000);

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

### Release Versions
```xml
<dependencies>
    <dependency>
        <groupId>com.github.rutledgepaulv</groupId>
        <artifactId>paging-streams</artifactId>
        <version>0.8</version>
    </dependency>
</dependencies>
```

### Snapshot Versions
```xml
<dependencies>
    <dependency>
        <groupId>com.github.rutledgepaulv</groupId>
        <artifactId>paging-streams</artifactId>
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

### License

This project is licensed under [MIT license](http://opensource.org/licenses/MIT).
