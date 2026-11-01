package archfixtures.violating.shop.domain;

import org.springframework.beans.factory.annotation.Autowired;

public class FieldInjection {
  @Autowired private Object dependency;
}
