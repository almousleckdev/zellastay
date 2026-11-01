@ApplicationModule(
    displayName = "Notification",
    allowedDependencies = {
      "shared",
      "platformconfig::api",
      "identity::api",
      "onboarding::api",
      "subscription::api",
      "organization::api",
      "catalog::api",
      "promotion::api",
      "booking::api",
      "payment::api",
      "chat::api",
      "review::api",
      "support::api",
      "loyalty::api"
    })
package com.zellastay.notification;

import org.springframework.modulith.ApplicationModule;
