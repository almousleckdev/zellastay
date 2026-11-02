package com.zellastay.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.domain.RefreshRotation.Decision;
import com.zellastay.shared.security.TokenAudience;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshRotationTest {

  private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");
  private static final SessionLifetime LIFETIME =
      new SessionLifetime(Duration.ofDays(30), Duration.ofDays(14));
  private static final Duration GRACE = Duration.ofSeconds(10);

  private static Session session() {
    return Session.start(
        UUID.randomUUID(), AccountId.random(), TokenAudience.CUSTOMER, false, T0, LIFETIME);
  }

  private static RefreshToken unused(Session session) {
    return new RefreshToken(UUID.randomUUID(), session.id(), "h", T0, null, null);
  }

  private static RefreshToken used(Session session, Instant usedAt) {
    return new RefreshToken(UUID.randomUUID(), session.id(), "h", T0, usedAt, UUID.randomUUID());
  }

  @Test
  void anUnusedTokenOfAnActiveSessionRotates() {
    Session session = session();

    assertThat(RefreshRotation.decide(session, unused(session), T0.plusSeconds(60), GRACE))
        .isEqualTo(Decision.ROTATE);
  }

  @Test
  void aTokenUsedJustNowIsToleratedAsARetry() {
    Session session = session();
    RefreshToken token = used(session, T0.plusSeconds(100));

    assertThat(RefreshRotation.decide(session, token, T0.plusSeconds(105), GRACE))
        .isEqualTo(Decision.RETRY_WITHIN_GRACE);
  }

  @Test
  void aTokenUsedLongAgoIsReuse() {
    Session session = session();
    RefreshToken token = used(session, T0.plusSeconds(100));

    assertThat(RefreshRotation.decide(session, token, T0.plusSeconds(111), GRACE))
        .isEqualTo(Decision.REUSED);
  }

  @Test
  void aUsedTokenWithoutAReplacementIsReuse() {
    Session session = session();
    RefreshToken token = new RefreshToken(UUID.randomUUID(), session.id(), "h", T0, T0, null);

    assertThat(RefreshRotation.decide(session, token, T0.plusSeconds(1), GRACE))
        .isEqualTo(Decision.REUSED);
  }

  @Test
  void aRevokedSessionIsInvalid() {
    Session session = session();
    session.revoke("logout", T0.plusSeconds(1));

    assertThat(RefreshRotation.decide(session, unused(session), T0.plusSeconds(2), GRACE))
        .isEqualTo(Decision.INVALID);
  }

  @Test
  void idleAndAbsoluteExpiryInvalidateTheSession() {
    Session idle = session();
    Session absolute = session();
    absolute.touch(T0.plus(Duration.ofDays(29)));

    assertThat(RefreshRotation.decide(idle, unused(idle), T0.plus(Duration.ofDays(15)), GRACE))
        .isEqualTo(Decision.INVALID);
    assertThat(
            RefreshRotation.decide(absolute, unused(absolute), T0.plus(Duration.ofDays(31)), GRACE))
        .isEqualTo(Decision.INVALID);
  }

  @Test
  void aTokenOfAnotherSessionIsInvalid() {
    Session session = session();
    RefreshToken foreign = unused(session());

    assertThat(RefreshRotation.decide(session, foreign, T0.plusSeconds(1), GRACE))
        .isEqualTo(Decision.INVALID);
  }
}
