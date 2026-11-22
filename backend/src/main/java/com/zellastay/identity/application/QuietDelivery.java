package com.zellastay.identity.application;

import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.VerificationDelivery;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends a message without ever failing the caller: a delivery problem must not change the HTTP
 * outcome (that would reveal whether an account exists). The user can request a resend.
 */
@Component
class QuietDelivery {

  private static final Logger LOG = LoggerFactory.getLogger(QuietDelivery.class);

  private final VerificationDelivery delivery;
  private final MeterRegistry meters;

  QuietDelivery(VerificationDelivery delivery, MeterRegistry meters) {
    this.delivery = delivery;
    this.meters = meters;
  }

  void send(DeliveryRequest request) {
    try {
      delivery.deliver(request);
    } catch (RuntimeException e) {
      meters
          .counter("identity.delivery.failures", "template", request.template().name())
          .increment();
      LOG.warn("delivery of {} failed ({})", request.template(), e.getClass().getSimpleName());
    }
  }
}
