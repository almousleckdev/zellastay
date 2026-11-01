@ApplicationModule(
    displayName = "Catalog",
    allowedDependencies = {
      "shared",
      "platformconfig::api",
      "organization::api",
      "onboarding::api",
      "subscription::api",
      "media::api",
      "audit::api"
    })
package com.zellastay.catalog;

import org.springframework.modulith.ApplicationModule;
