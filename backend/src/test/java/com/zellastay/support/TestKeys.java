package com.zellastay.support;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/** Throwaway EC P-256 keys for tests, in the PEM form the application reads. */
public final class TestKeys {

  public record Pair(String privatePem, String publicPem) {}

  private TestKeys() {}

  public static Pair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      KeyPair pair = generator.generateKeyPair();
      return new Pair(
          pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
          pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String pem(String type, byte[] encoded) {
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
  }
}
