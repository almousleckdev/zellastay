package archfixtures.violating.shop.web;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControllerWithExceptionHandler {
  @ExceptionHandler(IllegalStateException.class)
  public String handle() {
    return "x";
  }
}
