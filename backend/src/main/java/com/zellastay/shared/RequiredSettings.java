package com.zellastay.shared;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.env.Environment;

/**
 * Fails startup, before any bean is created, naming every required setting that is absent. Spring
 * leaves an unresolved {@code ${NAME}} placeholder as literal text, which would otherwise surface
 * as an unrelated error from whichever bean happens to be built first.
 */
final class RequiredSettings {

  static final List<String> NAMES =
      List.of("ZS_ENVIRONMENT", "ZS_DB_URL", "ZS_DB_USER", "ZS_DB_PASSWORD", "ZS_REDIS_HOST");

  private RequiredSettings() {}

  static BeanFactoryPostProcessor check(Environment environment) {
    return beanFactory -> {
      List<String> missing =
          NAMES.stream()
              .filter(
                  name ->
                      environment.getProperty(name) == null
                          || environment.getProperty(name).isBlank())
              .toList();
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required settings: " + String.join(", ", missing));
      }
    };
  }
}
