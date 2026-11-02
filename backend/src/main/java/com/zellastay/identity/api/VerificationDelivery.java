package com.zellastay.identity.api;

/**
 * Outbound port implemented by the notification module. Keeps one-time secrets out of persisted
 * events. Implementations must not store or log {@link DeliveryRequest#secret()}.
 */
public interface VerificationDelivery {

  void deliver(DeliveryRequest request);
}
