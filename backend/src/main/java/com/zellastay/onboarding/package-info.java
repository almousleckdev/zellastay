@ApplicationModule(
    displayName = "Onboarding",
    allowedDependencies = {
      "shared",
      "platformconfig::api",
      "identity::api",
      "media::api",
      "audit::api"
    })
package com.zellastay.onboarding;

import org.springframework.modulith.ApplicationModule;
