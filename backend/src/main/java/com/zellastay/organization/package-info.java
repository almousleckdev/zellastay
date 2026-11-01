@ApplicationModule(
    displayName = "Organization",
    allowedDependencies = {
      "shared",
      "identity::api",
      "onboarding::api",
      "subscription::api",
      "payment::api",
      "audit::api"
    })
package com.zellastay.organization;

import org.springframework.modulith.ApplicationModule;
