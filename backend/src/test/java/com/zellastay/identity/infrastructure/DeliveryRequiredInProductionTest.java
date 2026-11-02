package com.zellastay.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.ZellaStayApplication;
import com.zellastay.support.TestInfrastructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class DeliveryRequiredInProductionTest {

  @Test
  void productionAndStagingRefuseToStartWithoutARealVerificationDelivery() {
    for (String environment : List.of("PRODUCTION", "STAGING")) {
      assertThatThrownBy(() -> start(environment))
          .as(environment)
          .hasStackTraceContaining("VerificationDelivery");
    }
  }

  private static void start(String environment) {
    Map<String, String> settings =
        new java.util.HashMap<>(TestInfrastructure.applicationEnvironment());
    settings.put("ZS_ENVIRONMENT", environment);
    List<String> properties = new ArrayList<>();
    settings.forEach((key, value) -> properties.add(key + "=" + value));
    new SpringApplicationBuilder(ZellaStayApplication.class)
        .web(WebApplicationType.NONE)
        .properties(properties.toArray(String[]::new))
        .run()
        .close();
  }
}
