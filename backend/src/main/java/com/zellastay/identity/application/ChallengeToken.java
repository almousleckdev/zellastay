package com.zellastay.identity.application;

import java.util.Optional;
import java.util.UUID;

/**
 * The single-use bearer form of a challenge, {@code <challengeId>.<secret>}, used by password-reset
 * links and admin second-factor tickets. Parsing never reveals why a value was rejected.
 */
record ChallengeToken(UUID id, String secret) {

  static Optional<ChallengeToken> parse(String token) {
    if (token == null) {
      return Optional.empty();
    }
    String[] parts = token.split("\\.", -1);
    if (parts.length != 2) {
      return Optional.empty();
    }
    try {
      return Optional.of(new ChallengeToken(UUID.fromString(parts[0]), parts[1]));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  String value() {
    return id + "." + secret;
  }

  @Override
  public String toString() {
    return "ChallengeToken[]";
  }
}
