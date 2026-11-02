package com.zellastay.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Proves that the pinned dependency set supports the algorithms chosen in the Slice A design. */
class SecurityLibrariesTest {

  @Test
  void argon2idHashesVerifiesAndReportsWeakParameters() {
    Argon2PasswordEncoder current = new Argon2PasswordEncoder(16, 32, 1, 8192, 1);
    Argon2PasswordEncoder stronger = new Argon2PasswordEncoder(16, 32, 1, 16384, 2);

    String hash = current.encode("correct horse battery staple");

    assertThat(hash).startsWith("$argon2id$");
    assertThat(current.matches("correct horse battery staple", hash)).isTrue();
    assertThat(current.matches("wrong", hash)).isFalse();
    assertThat(stronger.matches("correct horse battery staple", hash)).isTrue();
    assertThat(stronger.upgradeEncoding(hash)).isTrue();
    assertThat(current.upgradeEncoding(hash)).isFalse();
  }

  @Test
  void es256TokensAreSignedAndVerifiedByKeyId() throws JOSEException {
    ECKey key = new ECKeyGenerator(Curve.P_256).keyID("k1").generate();
    NimbusJwtDecoder decoder = decoderFor(key.toPublicJWK());

    Jwt jwt = decoder.decode(sign(key, "k1"));

    assertThat(jwt.getSubject()).isEqualTo("account-1");
    assertThat(jwt.getAudience()).containsExactly("zs-customer");
  }

  @Test
  void tokensSignedByAnotherKeyAreRejected() throws JOSEException {
    ECKey trusted = new ECKeyGenerator(Curve.P_256).keyID("k1").generate();
    ECKey attacker = new ECKeyGenerator(Curve.P_256).keyID("k1").generate();
    NimbusJwtDecoder decoder = decoderFor(trusted.toPublicJWK());

    assertThatThrownBy(() -> decoder.decode(sign(attacker, "k1")))
        .isInstanceOf(BadJwtException.class);
  }

  private static NimbusJwtDecoder decoderFor(ECKey publicKey) {
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(
            JWSAlgorithm.ES256, new ImmutableJWKSet<>(new JWKSet(publicKey))));
    return new NimbusJwtDecoder(processor);
  }

  private static String sign(ECKey key, String keyId) throws JOSEException {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(),
            new JWTClaimsSet.Builder()
                .subject("account-1")
                .audience("zs-customer")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build());
    jwt.sign(new ECDSASigner(key));
    return jwt.serialize();
  }
}
