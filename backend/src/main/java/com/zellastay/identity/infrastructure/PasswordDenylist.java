package com.zellastay.identity.infrastructure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.io.ClassPathResource;

/** Loads the list of passwords that are refused outright, one per line, from the classpath. */
final class PasswordDenylist {

  private PasswordDenylist() {}

  static Set<String> fromClasspath(String resource) {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
      return parse(reader.lines());
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read the password denylist " + resource, e);
    }
  }

  /** Lower-cases and strips every entry; blank lines and {@code #} comments are skipped. */
  static Set<String> parse(Stream<String> lines) {
    return lines
        .map(String::strip)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .map(line -> line.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }
}
