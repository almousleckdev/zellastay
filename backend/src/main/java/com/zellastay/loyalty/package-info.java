@ApplicationModule(
    displayName = "Loyalty",
    allowedDependencies = {
      "shared",
      "identity::api",
      "booking::api",
      "promotion::api",
      "platformconfig::api",
      "audit::api"
    })
package com.zellastay.loyalty;

import org.springframework.modulith.ApplicationModule;
