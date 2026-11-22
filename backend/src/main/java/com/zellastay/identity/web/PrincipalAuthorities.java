package com.zellastay.identity.web;

import com.zellastay.identity.api.CurrentPrincipal;
import com.zellastay.identity.application.AdminPermissions;
import com.zellastay.shared.security.TokenAudience;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** Turns a verified principal into the authorities Spring Security checks. */
@Component
class PrincipalAuthorities {

  static final String MFA_VERIFIED = "MFA_VERIFIED";
  static final String PERMISSION_PREFIX = "PERM_";

  private final AdminPermissions adminPermissions;

  PrincipalAuthorities(AdminPermissions adminPermissions) {
    this.adminPermissions = adminPermissions;
  }

  Collection<GrantedAuthority> forPrincipal(CurrentPrincipal principal) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("AUDIENCE_" + principal.audience().name()));
    if (principal.mfaVerified()) {
      authorities.add(new SimpleGrantedAuthority(MFA_VERIFIED));
    }
    if (principal.audience() == TokenAudience.ADMIN && principal.mfaVerified()) {
      adminPermissions
          .of(principal.accountId())
          .forEach(
              permission ->
                  authorities.add(
                      new SimpleGrantedAuthority(PERMISSION_PREFIX + permission.code())));
    }
    return authorities;
  }
}
