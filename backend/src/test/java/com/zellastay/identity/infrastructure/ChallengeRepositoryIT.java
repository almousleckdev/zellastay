package com.zellastay.identity.infrastructure;

import static com.zellastay.support.AccountFixtures.uniqueEmail;
import static com.zellastay.support.RepositoryTestData.NOW;
import static com.zellastay.support.RepositoryTestData.newAccount;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.identity.api.AccountKind;
import com.zellastay.identity.domain.Account;
import com.zellastay.identity.domain.AccountRepository;
import com.zellastay.identity.domain.ChallengePurpose;
import com.zellastay.identity.domain.ChallengeRepository;
import com.zellastay.identity.domain.VerificationChallenge;
import com.zellastay.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Persistence of verification challenges. */
@IntegrationTest
class ChallengeRepositoryIT {

  @Autowired AccountRepository accounts;
  @Autowired ChallengeRepository challenges;
  @Autowired TransactionTemplate transaction;

  @Test
  void challengesRoundTripAndAreCountedPerWindow() {
    Account account = newAccount(AccountKind.CUSTOMER, uniqueEmail());
    transaction.executeWithoutResult(s -> accounts.insert(account));
    VerificationChallenge challenge =
        VerificationChallenge.issue(
            UUID.randomUUID(),
            account.id(),
            ChallengePurpose.EMAIL_VERIFICATION,
            "hash",
            NOW,
            Duration.ofMinutes(10));
    transaction.executeWithoutResult(s -> challenges.insert(challenge));

    VerificationChallenge loaded =
        challenges.findLatest(account.id(), ChallengePurpose.EMAIL_VERIFICATION).orElseThrow();
    loaded.check(false, NOW, 5);
    transaction.executeWithoutResult(s -> challenges.update(loaded));

    assertThat(challenges.findById(challenge.id()).orElseThrow().attempts()).isEqualTo(1);
    assertThat(
            challenges.countIssuedSince(
                account.id(), ChallengePurpose.EMAIL_VERIFICATION, NOW.minusSeconds(60)))
        .isEqualTo(1);
    assertThat(challenges.lastIssuedAt(account.id(), ChallengePurpose.EMAIL_VERIFICATION))
        .contains(NOW);
    assertThat(challenges.findLatest(account.id(), ChallengePurpose.PASSWORD_RESET)).isEmpty();
  }
}
