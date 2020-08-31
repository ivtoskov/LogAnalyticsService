package com.ivaylotoskov.controller;

import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
public class LogConsumerController {
  private static final String CONSUME_LOGS_ENDPOINT = "/api/consumeLogs";
  private static final String SUCCESS_MESSAGE = "Successfully consumed logs";
  private static final String LOGS_TOPIC_NAME = "logs";

  @Autowired
  private KafkaTemplate<Object, Object> template;

  @PostMapping(CONSUME_LOGS_ENDPOINT)
  public String consumeLogs(@RequestBody ArrayList<String> logLines) {
    template.send(LOGS_TOPIC_NAME, SerializationUtils.serialize(logLines));
    return SUCCESS_MESSAGE;
  }
}
