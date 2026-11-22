package com.zellastay.identity.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.identity.api.PasswordRejectedException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  private final PasswordPolicy policy = new PasswordPolicy(Set.of("password123", "qwertyuiop"));
  private final Email email = Email.of("jane.doe@example.com");

  @Test
  void acceptsTenAndOneHundredTwentyEightCharacters() {
    assertThatCode(() -> policy.check("x7!Qm2#vL9", email)).doesNotThrowAnyException();
    assertThatCode(() -> policy.check("a1".repeat(64), email)).doesNotThrowAnyException();
  }

  @Test
  void rejectsNineAndOneHundredTwentyNineCharacters() {
    assertThatThrownBy(() -> policy.check("x7!Qm2#vL", email))
        .isInstanceOf(PasswordRejectedException.class);
    assertThatThrownBy(() -> policy.check("a".repeat(129), email))
        .isInstanceOf(PasswordRejectedException.class);
  }

  @Test
  void rejectsDenylistedPasswordsRegardlessOfCase() {
    assertThatThrownBy(() -> policy.check("PassWord123", email))
        .isInstanceOf(PasswordRejectedException.class);
    assertThatThrownBy(() -> policy.check("qwertyuiop", email))
        .isInstanceOf(PasswordRejectedException.class);
  }

  @Test
  void rejectsThePasswordEqualToTheEmailOrItsLocalPart() {
    assertThatThrownBy(() -> policy.check("jane.doe@example.com", email))
        .isInstanceOf(PasswordRejectedException.class);
    assertThatThrownBy(() -> policy.check("JANE.DOEXX", Email.of("jane.doexx@example.com")))
        .isInstanceOf(PasswordRejectedException.class);
  }

  @Test
  void treatsCompatibilityEquivalentCharactersAsTheirNormalForm() {
    // fullwidth "ｐａｓｓｗｏｒｄ１２３" normalizes (NFKC) to the denylisted "password123"
    assertThatThrownBy(() -> policy.check("ｐａｓｓｗｏｒｄ１２３", email))
        .isInstanceOf(PasswordRejectedException.class);
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> policy.check(null, email))
        .isInstanceOf(PasswordRejectedException.class);
  }
}
