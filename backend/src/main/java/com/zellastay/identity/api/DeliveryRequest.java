package com.zellastay.identity.api;

import java.util.Objects;

/**
 * One message to send. {@code secret} (a code or a link token) is transient: it is handed to the
 * sender once and never stored or logged, which is why {@link #toString()} omits it.
 */
public record DeliveryRequest(
    DeliveryTemplate template, String recipientEmail, String locale, String secret) {

  public DeliveryRequest {
    Objects.requireNonNull(template, "template");
    Objects.requireNonNull(recipientEmail, "recipientEmail");
    Objects.requireNonNull(locale, "locale");
  }

  @Override
  public String toString() {
    return "DeliveryRequest[template=" + template + ", locale=" + locale + "]";
  }
}
