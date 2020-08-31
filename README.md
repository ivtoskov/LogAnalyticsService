# LogAnalyticsService

This is a simple project for logs aggregation that uses Spark. It consists of a REST service that consumes batches of
log lines in the [Common Logfile Format](https://www.w3.org/Daemon/User/Config/Logging.html) and sends them to a locally
running Kafka. The `SparkLogsAggregator` is a Spark Streaming application that consumes from the same topic and prints
every 30 seconds the 10 most visited sections in the last minute.

## Design decisions

* Build - Maven, since it is mature and de-facto standard for Java projects.
* HTTP REST service - Spring Boot, since it is very mature and quite easy to setup. I opted for a thin and stateless
    service that will be easy to maintain and could scale horizontally.
* Event streaming - Kafka, since it offers a high-throughput, low-latency, fault-tolerant solution for handling
    real-time data feeds.
* Stream processing - Spark Streaming, since it enables scalable, high-throughput, fault-tolerant stream processing of
    live data streams. DStreams were chosen over Structured Streaming mostly due to familiarity.

## Build

`mvn package`

## Run

### Kafka
In order to run this project you need a locally running Kafka on the standard 9092 port (consult the
[Apache Kafka Quickstart Guide](https://kafka.apache.org/quickstart)).

### HttpLogConsumer

`java -jar HttpLogConsumer/target/HttpLogConsumer-1.0-SNAPSHOT.jar`

### SparkLogsAggregator

`java -jar SparkLogsAggregator/target/SparkLogsAggregator-1.0-SNAPSHOT.jar`

### Example request

```
curl --location --request POST 'localhost:8080/api/consumeLogs' \
--header 'Content-Type: application/json' \
--data-raw '[
    "192.168.4.25 - - [22/Dec/2016:17:28:58 +0300] \"GET /index.php/component/search/ HTTP/1.1\" 200 3052",
    "192.168.4.25 - - [22/Dec/2016:18:28:59 +0300] \"GET /locations HTTP/1.1\" 200 3052",
    "192.168.4.25 - - [22/Dec/2016:18:29:59 +0300] \"GET /shop/item1 HTTP/1.1\" 200 3052",
    "192.168.4.25 - - [22/Dec/2016:18:39:59 +0300] \"GET /shop/item2 HTTP/1.1\" 200 3052",
    "192.168.4.25 - - [22/Dec/2016:18:40:15 +0300] \"GET /locations/paris HTTP/1.1\" 200 3052",
    "192.168.4.25 - - [22/Dec/2016:18:40:15 +0300] \"GET /images/img1 HTTP/1.1\" 200 3052",
    "192.168.4.25 - - [22/Dec/2016:18:40:19 +0300] \"GET /images/img2 HTTP/1.1\" 200 3052",
    "192.168.4.25 - - [22/Dec/2016:18:40:21 +0300] \"GET /images/img3 HTTP/1.1\" 200 3052",
    "192.168.4.25 - - [22/Dec/2016:18:49:17 +0300] \"GET /index.php/browse HTTP/1.1\" 200 3052",
    "192.168.4.25 - - [22/Dec/2016:18:49:22 +0300] \"GET /about HTTP/1.1\" 200 3052"
]
'
```