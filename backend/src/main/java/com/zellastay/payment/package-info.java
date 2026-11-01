@ApplicationModule(
    displayName = "Payment",
    allowedDependencies = {"shared", "platformconfig::api", "identity::api", "audit::api"})
package com.zellastay.payment;

import org.springframework.modulith.ApplicationModule;
