@ApplicationModule(
    displayName = "Pricing",
    allowedDependencies = {
      "shared",
      "platformconfig::api",
      "identity::api",
      "inventory::api",
      "audit::api"
    })
package com.zellastay.pricing;

import org.springframework.modulith.ApplicationModule;
