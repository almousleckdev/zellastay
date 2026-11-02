package com.zellastay.audit.domain;

/** Facts about the current request; every field is null outside a web request. */
public record RequestMetadata(String ip, String userAgent, String requestId) {

  public static RequestMetadata none() {
    return new RequestMetadata(null, null, null);
  }
}
