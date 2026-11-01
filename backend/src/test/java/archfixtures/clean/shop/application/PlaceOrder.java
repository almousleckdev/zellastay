package archfixtures.clean.shop.application;

import archfixtures.clean.shop.domain.Order;
import archfixtures.clean.shop.domain.OrderStore;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

public class PlaceOrder {
  private final OrderStore store;
  private final Clock clock;

  public PlaceOrder(OrderStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  @Transactional
  public void place() {
    store.save(new Order(clock));
  }
}
