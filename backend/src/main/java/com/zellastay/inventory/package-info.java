@ApplicationModule(
    displayName = "Inventory",
    allowedDependencies = {
      "shared",
      "catalog::api",
      "organization::api",
      "subscription::api",
      "audit::api"
    })
package com.zellastay.inventory;

import org.springframework.modulith.ApplicationModule;
