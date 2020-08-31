package com.ivaylotoskov;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;
import scala.Tuple2;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogsAggregatorApplication {
  private static final Set<String> BANNED_SECTIONS = Set.of("/images");
  private static final String COMMON_LOG_FORMAT_REGEX =
      "^[\\d.]+ \\S+ \\S+ \\[[\\w:/]+\\s[+\\-]\\d{4}] \"[A-Z]+ (/[^ /]+)\\S* \\S+\" \\d{3} \\d+$";
  private static final Pattern COMMON_LOG_FORMAT_PATTERN = Pattern.compile(COMMON_LOG_FORMAT_REGEX);
  private static final int SECTION_GROUP_ID = 1;
  private static final String SECTION_COUNT_STRING_FORMAT = "%d - %s";
  private static final Duration WINDOW_DURATION = Durations.seconds(60);
  private static final Duration SLIDE_DURATION = Durations.seconds(30);
  private static final int NUM_ENTRIES_TO_PRINT = 10;
  private static final String LOGS_TOPIC_NAME = "logs";

  public static void main(String[] args) throws InterruptedException {
    // Setup streaming context
    SparkConf conf = new SparkConf().setMaster("local[2]").setAppName(LogsAggregatorApplication.class.getSimpleName());
    JavaStreamingContext streamingContext = new JavaStreamingContext(conf, Durations.seconds(5));

    // Create a Kafka stream
    Map<String, Object> kafkaParams = getKafkaParams();
    Collection<String> topics = Collections.singletonList(LOGS_TOPIC_NAME);
    JavaInputDStream<ConsumerRecord<String, byte[]>> stream = KafkaUtils.createDirectStream(
        streamingContext,
        LocationStrategies.PreferConsistent(),
        ConsumerStrategies.Subscribe(topics, kafkaParams));

    // Compute the section counts over a window
    Broadcast<Set<String>> bannedSections = streamingContext.sparkContext().broadcast(BANNED_SECTIONS);
    JavaDStream<String> sectionCounts = stream
        .map(ConsumerRecord::value) // Get the message value as a byte array
        .map(SerializationUtils::<List<String>>deserialize) // deserialize the byte array to a list of strings
        .flatMap(List::iterator) // flatMap to get the individual logs
        .map(COMMON_LOG_FORMAT_PATTERN::matcher) // get a regex matcher for the common log format
        .filter(Matcher::matches) // filter out any logs that do not match the format
        .map(matcher -> matcher.group(SECTION_GROUP_ID)) // get the section
        .filter(section -> !bannedSections.value().contains(section)) // remove all banned sections
        .mapToPair(section -> new Tuple2<>(section, 1)) // map each section to a count of one
        .reduceByKeyAndWindow(Integer::sum, WINDOW_DURATION, SLIDE_DURATION) // sum all sections over a window
        .mapToPair(Tuple2::swap) // make the section count the tuple key, so that it can be used for sorting
        .transformToPair(sectionCount -> sectionCount.sortByKey(false)) // sort descending by counts
        .map(LogsAggregatorApplication::sectionCountToString); // map to the desired string format
    sectionCounts.print(NUM_ENTRIES_TO_PRINT); // print top N entries

    streamingContext.start();
    streamingContext.awaitTermination();
  }

  private static Map<String, Object> getKafkaParams() {
    Map<String, Object> kafkaParams = new HashMap<>();
    kafkaParams.put("bootstrap.servers", "localhost:9092");
    kafkaParams.put("key.deserializer", StringDeserializer.class);
    kafkaParams.put("value.deserializer", ByteArrayDeserializer.class);
    kafkaParams.put("group.id", "logs_aggregator");
    kafkaParams.put("auto.offset.reset", "latest");
    kafkaParams.put("enable.auto.commit", true);

    return kafkaParams;
  }

  private static String sectionCountToString(Tuple2<Integer, String> sectionCount) {
    return String.format(SECTION_COUNT_STRING_FORMAT, sectionCount._1(), sectionCount._2());
  }
}
