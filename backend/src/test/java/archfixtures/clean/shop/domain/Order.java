package archfixtures.clean.shop.domain;

import java.time.Clock;
import java.time.Instant;

public class Order {
  private final Instant placedAt;

  public Order(Clock clock) {
    this.placedAt = clock.instant();
  }

  public Instant placedAt() {
    return placedAt;
  }
}
