@ApplicationModule(
    displayName = "Reporting",
    allowedDependencies = {
      "shared",
      "booking::api",
      "payment::api",
      "subscription::api",
      "organization::api",
      "catalog::api",
      "review::api"
    })
package com.zellastay.reporting;

import org.springframework.modulith.ApplicationModule;
