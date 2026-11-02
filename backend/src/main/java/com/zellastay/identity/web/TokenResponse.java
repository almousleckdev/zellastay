package com.zellastay.identity.web;

import com.zellastay.identity.application.TokenPair;

record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {

  static TokenResponse from(TokenPair pair) {
    return new TokenResponse(
        pair.accessToken(), "Bearer", pair.expiresInSeconds(), pair.refreshToken());
  }

  @Override
  public String toString() {
    return "TokenResponse[]";
  }
}
