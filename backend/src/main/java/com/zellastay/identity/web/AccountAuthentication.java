package com.zellastay.identity.web;

import com.zellastay.identity.api.CurrentPrincipal;
import java.io.Serial;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** A request authenticated by a valid access token whose session is still usable. */
class AccountAuthentication extends AbstractAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final transient CurrentPrincipal principal;

  AccountAuthentication(
      CurrentPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = principal;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return "";
  }

  @Override
  public CurrentPrincipal getPrincipal() {
    return principal;
  }
}
