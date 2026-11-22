package com.zellastay.identity.infrastructure;

import com.zellastay.identity.domain.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public class Argon2PasswordHasher implements PasswordHasher {

  private static final int SALT_LENGTH = 16;
  private static final int HASH_LENGTH = 32;
  private static final String DUMMY_PASSWORD = "dummy-password-for-timing-equalization";

  private final Argon2PasswordEncoder encoder;
  private final String dummyHash;

  public Argon2PasswordHasher(int memoryKib, int iterations, int parallelism) {
    this.encoder =
        new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, parallelism, memoryKib, iterations);
    this.dummyHash = encoder.encode(DUMMY_PASSWORD);
  }

  @Override
  public String hash(String password) {
    return encoder.encode(password);
  }

  @Override
  public boolean matches(String password, String hash) {
    return encoder.matches(password, hash);
  }

  @Override
  public boolean needsRehash(String hash) {
    return encoder.upgradeEncoding(hash);
  }

  @Override
  public void burn(String password) {
    encoder.matches(password == null ? DUMMY_PASSWORD : password, dummyHash);
  }
}
