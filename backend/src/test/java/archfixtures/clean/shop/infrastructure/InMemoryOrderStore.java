package archfixtures.clean.shop.infrastructure;

import archfixtures.clean.shop.domain.Order;
import archfixtures.clean.shop.domain.OrderStore;

public class InMemoryOrderStore implements OrderStore {
  @Override
  public void save(Order order) {}
}
