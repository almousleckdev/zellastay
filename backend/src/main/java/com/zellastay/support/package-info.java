@ApplicationModule(
    displayName = "Support",
    allowedDependencies = {
      "shared",
      "identity::api",
      "booking::api",
      "payment::api",
      "organization::api",
      "media::api",
      "audit::api"
    })
package com.zellastay.support;

import org.springframework.modulith.ApplicationModule;
