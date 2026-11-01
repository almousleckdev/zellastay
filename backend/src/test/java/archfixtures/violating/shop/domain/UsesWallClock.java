package archfixtures.violating.shop.domain;

import java.time.Instant;

public class UsesWallClock {
  public Instant when() {
    return Instant.now();
  }
}
