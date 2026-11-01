@ApplicationModule(
    displayName = "Promotion",
    allowedDependencies = {
      "shared",
      "platformconfig::api",
      "organization::api",
      "catalog::api",
      "audit::api"
    })
package com.zellastay.promotion;

import org.springframework.modulith.ApplicationModule;
