package archfixtures.clean.shop.web;

import archfixtures.clean.shop.domain.OutOfStockException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ShopAdvice {
  @ExceptionHandler(OutOfStockException.class)
  String handle() {
    return "out-of-stock";
  }
}
