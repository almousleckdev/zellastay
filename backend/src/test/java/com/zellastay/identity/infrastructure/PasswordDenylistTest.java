package com.zellastay.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PasswordDenylistTest {

  @Test
  void entriesAreStrippedAndLowerCased() {
    assertThat(PasswordDenylist.parse(Stream.of("  Password123 ", "QWERTY")))
        .containsExactlyInAnyOrder("password123", "qwerty");
  }

  @Test
  void blankLinesAndCommentsAreIgnored() {
    assertThat(PasswordDenylist.parse(Stream.of("# header", "", "   ", "letmein", "#comment")))
        .containsExactly("letmein");
  }

  @Test
  void duplicatesCollapseIntoOneEntry() {
    assertThat(PasswordDenylist.parse(Stream.of("abc", "ABC", " abc "))).containsExactly("abc");
  }

  @Test
  void theBundledListIsPresentAndNotEmpty() {
    assertThat(PasswordDenylist.fromClasspath("identity/password-denylist.txt"))
        .isNotEmpty()
        .doesNotContain("");
  }
}
