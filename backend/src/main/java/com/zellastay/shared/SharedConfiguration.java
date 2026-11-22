package com.zellastay.shared;

import java.time.Clock;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RuntimeProperties.class)
class SharedConfiguration {

  @Bean
  static BeanFactoryPostProcessor requiredSettingsCheck(Environment environment) {
    return RequiredSettings.check(environment);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
