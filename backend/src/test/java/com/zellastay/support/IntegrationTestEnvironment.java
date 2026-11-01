package com.zellastay.support;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class IntegrationTestEnvironment
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    TestInfrastructure.applicationEnvironment()
        .forEach((key, value) -> TestPropertyValues.of(key + "=" + value).applyTo(context));
  }
}
