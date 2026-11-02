package com.zellastay.shared.security;

import java.util.List;

/**
 * Routes a module exposes to one audience. Identity's security chains allow only the registered
 * patterns (everything else is denied), so adding an endpoint means registering its route here.
 */
public interface AudienceRoutes {

  TokenAudience audience();

  /** Ant-style patterns, for example {@code /api/v1/vendor/organizations/**}. */
  List<String> patterns();
}
