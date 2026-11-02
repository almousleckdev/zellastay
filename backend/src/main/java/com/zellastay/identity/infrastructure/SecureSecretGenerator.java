package com.zellastay.identity.infrastructure;

import com.zellastay.identity.domain.SecretGenerator;
import java.security.SecureRandom;
import java.util.Base64;

public class SecureSecretGenerator implements SecretGenerator {

  private static final int CODE_BOUND = 1_000_000;
  private static final int TOKEN_BYTES = 32;

  private final SecureRandom random = new SecureRandom();

  @Override
  public String numericCode() {
    return "%06d".formatted(random.nextInt(CODE_BOUND));
  }

  @Override
  public String token() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
