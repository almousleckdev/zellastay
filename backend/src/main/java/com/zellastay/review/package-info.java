@ApplicationModule(
    displayName = "Review",
    allowedDependencies = {
      "shared",
      "identity::api",
      "booking::api",
      "catalog::api",
      "media::api",
      "audit::api"
    })
package com.zellastay.review;

import org.springframework.modulith.ApplicationModule;
