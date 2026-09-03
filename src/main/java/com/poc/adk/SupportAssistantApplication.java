package com.poc.adk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.poc.adk", "com.google.adk.web"})
public class SupportAssistantApplication {

  public static void main(String[] args) {
    SpringApplication.run(SupportAssistantApplication.class, args);
  }
}
