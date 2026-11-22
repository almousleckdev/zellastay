package com.zellastay.identity.infrastructure;

import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.api.VerificationDelivery;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Development and test stand-in for the notification module (registered only in LOCAL and CI). It
 * keeps the most recent messages in memory so tests and developers can read the secrets; staging
 * and production refuse to start without a real implementation.
 */
public class InMemoryVerificationDelivery implements VerificationDelivery {

  private static final int CAPACITY = 1000;

  private final Deque<DeliveryRequest> messages = new ArrayDeque<>();

  @Override
  public synchronized void deliver(DeliveryRequest request) {
    if (messages.size() == CAPACITY) {
      messages.removeFirst();
    }
    messages.addLast(request);
  }

  public synchronized Optional<DeliveryRequest> latest(String email, DeliveryTemplate template) {
    DeliveryRequest found = null;
    for (DeliveryRequest message : messages) {
      if (message.recipientEmail().equals(email) && message.template() == template) {
        found = message;
      }
    }
    return Optional.ofNullable(found);
  }
}
