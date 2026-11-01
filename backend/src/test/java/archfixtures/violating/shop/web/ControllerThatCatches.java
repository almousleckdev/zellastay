package archfixtures.violating.shop.web;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControllerThatCatches {
  public int parse(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }
}
