@ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {"shared", "platformconfig::api", "audit::api"})
package com.zellastay.identity;

import org.springframework.modulith.ApplicationModule;
