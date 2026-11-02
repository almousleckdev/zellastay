package com.zellastay.identity.infrastructure;

import com.zellastay.identity.application.AdminBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Runs the one-time Super Admin creation at startup, only when explicitly enabled. */
@Component
class AdminBootstrapRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapRunner.class);

  private final IdentityProperties properties;
  private final AdminBootstrapService bootstrap;

  AdminBootstrapRunner(IdentityProperties properties, AdminBootstrapService bootstrap) {
    this.properties = properties;
    this.bootstrap = bootstrap;
  }

  @Override
  public void run(ApplicationArguments args) {
    IdentityProperties.Bootstrap settings = properties.bootstrap();
    if (!settings.enabled()) {
      return;
    }
    AdminBootstrapService.Result result =
        bootstrap.bootstrap(settings.email(), settings.password());
    LOG.info("admin bootstrap finished: {}", result);
  }
}
