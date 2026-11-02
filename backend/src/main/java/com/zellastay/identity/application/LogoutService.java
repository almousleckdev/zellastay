package com.zellastay.identity.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.RefreshTokenRepository;
import com.zellastay.identity.domain.SecretHasher;
import com.zellastay.identity.domain.Session;
import com.zellastay.identity.domain.SessionRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Ends the session a refresh token belongs to. Idempotent and silent about unknown tokens. */
@Service
public class LogoutService {

  private final RefreshTokenRepository refreshTokens;
  private final SessionRepository sessions;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final SessionRevoker revoker;
  private final AuditRecorder audit;
  private final TransactionTemplate transaction;

  LogoutService(
      RefreshTokenRepository refreshTokens,
      SessionRepository sessions,
      AccountRepository accounts,
      SecretHasher hasher,
      SessionRevoker revoker,
      AuditRecorder audit,
      TransactionTemplate transaction) {
    this.refreshTokens = refreshTokens;
    this.sessions = sessions;
    this.accounts = accounts;
    this.hasher = hasher;
    this.revoker = revoker;
    this.audit = audit;
    this.transaction = transaction;
  }

  public void logout(String presented) {
    if (presented == null || presented.isBlank()) {
      return;
    }
    Optional<Account> ended =
        transaction.execute(
            status ->
                refreshTokens
                    .findByHash(hasher.digest(presented))
                    .flatMap(token -> sessions.findById(token.sessionId()))
                    .filter(session -> session.revokedAt() == null)
                    .flatMap(this::end));
    ended.ifPresent(
        account ->
            audit.recordBestEffort(
                new AuditEntry(
                    AuditActors.actor(account),
                    IdentityAuditAction.LOGOUT,
                    AuditActors.subject(account),
                    AuditOutcome.SUCCESS,
                    Map.of())));
  }

  private Optional<Account> end(Session session) {
    revoker.revoke(session, "logout");
    return accounts.findById(session.accountId());
  }
}
