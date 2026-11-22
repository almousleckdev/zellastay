@ApplicationModule(
    displayName = "Subscription",
    allowedDependencies = {
      "shared",
      "platformconfig::api",
      "identity::api",
      "payment::api",
      "onboarding::api",
      "audit::api"
    })
package com.zellastay.subscription;

import org.springframework.modulith.ApplicationModule;
