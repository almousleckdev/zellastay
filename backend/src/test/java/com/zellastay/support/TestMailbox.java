package com.zellastay.support;

import com.zellastay.identity.api.DeliveryRequest;
import com.zellastay.identity.api.DeliveryTemplate;
import com.zellastay.identity.infrastructure.InMemoryVerificationDelivery;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Reads what the application "sent" through the in-memory delivery used in local and CI runs. */
@Component
public class TestMailbox {

  private final InMemoryVerificationDelivery delivery;

  TestMailbox(InMemoryVerificationDelivery delivery) {
    this.delivery = delivery;
  }

  public Optional<DeliveryRequest> latest(String email, DeliveryTemplate template) {
    return delivery.latest(email, template);
  }

  public String verificationCode(String email) {
    return secret(email, DeliveryTemplate.EMAIL_VERIFICATION);
  }

  public String resetToken(String email) {
    return secret(email, DeliveryTemplate.PASSWORD_RESET);
  }

  private String secret(String email, DeliveryTemplate template) {
    return latest(email, template).orElseThrow().secret();
  }
}
