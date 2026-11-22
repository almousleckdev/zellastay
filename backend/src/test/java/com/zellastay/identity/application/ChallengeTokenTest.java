package com.zellastay.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChallengeTokenTest {

  private static final UUID ID = UUID.fromString("0b9c9b3e-5d6f-4c63-9a0e-8f1a2b3c4d5e");

  @Test
  void aTokenIsTheChallengeIdAndTheSecretJoinedByADot() {
    ChallengeToken token = new ChallengeToken(ID, "s3cret");

    assertThat(token.value()).isEqualTo(ID + ".s3cret");
    assertThat(ChallengeToken.parse(token.value())).contains(token);
  }

  @Test
  void anEmptySecretStillParsesSoTheSecretCheckDecidesTheOutcome() {
    assertThat(ChallengeToken.parse(ID + ".")).contains(new ChallengeToken(ID, ""));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "nodot", "not-a-uuid.secret", "a.b.c", ".secret", " "})
  void malformedTokensDoNotParse(String value) {
    assertThat(ChallengeToken.parse(value)).isEmpty();
  }

  @Test
  void theSecretNeverAppearsInToString() {
    assertThat(new ChallengeToken(ID, "s3cret").toString()).doesNotContain("s3cret");
  }
}
