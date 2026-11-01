@ApplicationModule(
    displayName = "Booking",
    allowedDependencies = {
      "shared",
      "platformconfig::api",
      "identity::api",
      "organization::api",
      "catalog::api",
      "inventory::api",
      "pricing::api",
      "promotion::api",
      "payment::api",
      "audit::api"
    })
package com.zellastay.booking;

import org.springframework.modulith.ApplicationModule;
