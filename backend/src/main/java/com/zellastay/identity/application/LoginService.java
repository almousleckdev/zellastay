package com.zellastay.identity.application;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.domain.Account;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Password login for customers and vendor users (admins also need a second factor). */
@Service
public class LoginService {

  private final CredentialAuthenticator authenticator;
  private final SessionIssuer sessions;
  private final IdentityAuditTrail trail;
  private final TransactionTemplate transaction;

  LoginService(
      CredentialAuthenticator authenticator,
      SessionIssuer sessions,
      IdentityAuditTrail trail,
      TransactionTemplate transaction) {
    this.authenticator = authenticator;
    this.sessions = sessions;
    this.trail = trail;
    this.transaction = transaction;
  }

  public TokenPair login(AccountKind kind, String email, String password) {
    Account account = authenticator.authenticate(kind, email, password);
    TokenPair pair = transaction.execute(status -> sessions.start(account, false));
    trail.succeededBestEffort(
        account, IdentityAuditAction.LOGIN_SUCCESS, Map.of("audience", kind.audience().value()));
    return pair;
  }
}
