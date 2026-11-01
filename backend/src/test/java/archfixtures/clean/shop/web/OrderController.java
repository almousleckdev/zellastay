package archfixtures.clean.shop.web;

import archfixtures.clean.shop.application.PlaceOrder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
  private final PlaceOrder placeOrder;

  public OrderController(PlaceOrder placeOrder) {
    this.placeOrder = placeOrder;
  }

  public void create() {
    placeOrder.place();
  }
}
