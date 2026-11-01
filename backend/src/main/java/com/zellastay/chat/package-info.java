@ApplicationModule(
    displayName = "Chat",
    allowedDependencies = {
      "shared",
      "identity::api",
      "booking::api",
      "organization::api",
      "media::api",
      "audit::api"
    })
package com.zellastay.chat;

import org.springframework.modulith.ApplicationModule;
