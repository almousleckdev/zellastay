package com.zellastay.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zellastay.identity.api.EmailInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

  @Test
  void normalizesCaseAndWhitespace() {
    assertThat(Email.of("  Jane.Doe@Example.COM ").value()).isEqualTo("jane.doe@example.com");
  }

  @Test
  void masksLocalPartAndDomain() {
    assertThat(Email.of("jane.doe@example.com").masked()).isEqualTo("j***@e***.com");
  }

  @Test
  void exposesTheLocalPart() {
    assertThat(Email.of("jane.doe@example.com").localPart()).isEqualTo("jane.doe");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "plain", "a@b", "@example.com", "a b@example.com", "a@@example.com"})
  void rejectsMalformedAddresses(String raw) {
    assertThatThrownBy(() -> Email.of(raw)).isInstanceOf(EmailInvalidException.class);
  }

  @Test
  void rejectsNullAndOverlongAddresses() {
    assertThatThrownBy(() -> Email.of(null)).isInstanceOf(EmailInvalidException.class);
    assertThatThrownBy(() -> Email.of("a".repeat(250) + "@example.com"))
        .isInstanceOf(EmailInvalidException.class);
  }
}
