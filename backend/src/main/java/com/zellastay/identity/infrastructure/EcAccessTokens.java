package com.zellastay.identity.infrastructure;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.zellastay.identity.api.AccountId;
import com.zellastay.identity.domain.AccessTokens;
import com.zellastay.shared.security.TokenAudience;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

/** ES256 access tokens. One signing key plus optional retired public keys for rotation. */
public class EcAccessTokens implements AccessTokens {

  private static final String CURVE = "secp256r1";

  private final String issuer;
  private final String keyId;
  private final ECPrivateKey signingKey;
  private final Map<String, ECPublicKey> verificationKeys = new HashMap<>();
  private final Duration ttl;

  public EcAccessTokens(
      String issuer,
      String keyId,
      String privateKeyPem,
      Map<String, String> retiredPublicKeysPem,
      Duration ttl) {
    this.issuer = issuer;
    this.keyId = keyId;
    this.ttl = ttl;
    this.signingKey = readPrivateKey(privateKeyPem);
    verificationKeys.put(keyId, derivePublicKey(signingKey));
    retiredPublicKeysPem.forEach((id, pem) -> verificationKeys.put(id, readPublicKey(pem)));
  }

  @Override
  public IssuedToken issue(
      AccountId accountId,
      TokenAudience audience,
      UUID sessionId,
      boolean mfaVerified,
      Instant now) {
    Instant expiresAt = now.plus(ttl);
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(accountId.value().toString())
            .audience(audience.value())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiresAt))
            .jwtID(UUID.randomUUID().toString())
            .claim("sid", sessionId.toString())
            .claim("amr", mfaVerified ? List.of("pwd", "mfa") : List.of("pwd"))
            .build();
    try {
      SignedJWT jwt =
          new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims);
      jwt.sign(new ECDSASigner(signingKey));
      return new IssuedToken(jwt.serialize(), expiresAt);
    } catch (JOSEException e) {
      throw new IllegalStateException("cannot sign access token", e);
    }
  }

  @Override
  public Optional<TokenClaims> parse(String token, TokenAudience expectedAudience, Instant now) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      ECPublicKey key = verificationKeys.get(jwt.getHeader().getKeyID());
      if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm())
          || key == null
          || !jwt.verify(new ECDSAVerifier(key))) {
        return Optional.empty();
      }
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      Instant expiresAt = claims.getExpirationTime().toInstant();
      boolean valid =
          issuer.equals(claims.getIssuer())
              && claims.getAudience().contains(expectedAudience.value())
              && now.isBefore(expiresAt);
      if (!valid) {
        return Optional.empty();
      }
      List<String> amr = claims.getStringListClaim("amr");
      return Optional.of(
          new TokenClaims(
              new AccountId(UUID.fromString(claims.getSubject())),
              expectedAudience,
              UUID.fromString(claims.getStringClaim("sid")),
              amr != null && amr.contains("mfa"),
              expiresAt));
    } catch (java.text.ParseException | JOSEException | RuntimeException e) {
      return Optional.empty();
    }
  }

  private static ECPrivateKey readPrivateKey(String pem) {
    try {
      return (ECPrivateKey)
          KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der(pem)));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException(
          "ZS_IDENTITY_TOKEN_SIGNING_KEY is not a valid PKCS#8 EC key", e);
    }
  }

  private static ECPublicKey readPublicKey(String pem) {
    try {
      return (ECPublicKey)
          KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der(pem)));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("a retired verification key is not a valid X.509 EC key", e);
    }
  }

  private static ECPublicKey derivePublicKey(ECPrivateKey privateKey) {
    try {
      ECNamedCurveParameterSpec curve = ECNamedCurveTable.getParameterSpec(CURVE);
      org.bouncycastle.math.ec.ECPoint point = curve.getG().multiply(privateKey.getS()).normalize();
      java.security.spec.ECPoint w =
          new java.security.spec.ECPoint(
              point.getAffineXCoord().toBigInteger(), point.getAffineYCoord().toBigInteger());
      return (ECPublicKey)
          KeyFactory.getInstance("EC")
              .generatePublic(new ECPublicKeySpec(w, privateKey.getParams()));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot derive the public key", e);
    }
  }

  private static byte[] der(String pem) {
    String body = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
    return Base64.getDecoder().decode(body);
  }
}
