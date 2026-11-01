@ApplicationModule(
    displayName = "Search",
    allowedDependencies = {
      "shared",
      "platformconfig::api",
      "catalog::api",
      "inventory::api",
      "pricing::api",
      "review::api",
      "subscription::api"
    })
package com.zellastay.search;

import org.springframework.modulith.ApplicationModule;
