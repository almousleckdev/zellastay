package com.zellastay.support;

import java.lang.reflect.Field;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Gives every test instance an {@link ApiClient} for the running application in its fields. */
public class ApiClientExtension implements TestInstancePostProcessor {

  @Override
  public void postProcessTestInstance(Object testInstance, ExtensionContext context)
      throws IllegalAccessException {
    ApiClient client = new ApiClient(serverPort(context));
    for (Field field : testInstance.getClass().getDeclaredFields()) {
      if (field.getType() == ApiClient.class) {
        field.setAccessible(true);
        field.set(testInstance, client);
      }
    }
  }

  private static int serverPort(ExtensionContext context) {
    return SpringExtension.getApplicationContext(context)
        .getEnvironment()
        .getRequiredProperty("local.server.port", Integer.class);
  }
}
