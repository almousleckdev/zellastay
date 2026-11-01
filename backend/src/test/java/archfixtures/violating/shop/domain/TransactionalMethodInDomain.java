package archfixtures.violating.shop.domain;

import org.springframework.transaction.annotation.Transactional;

public class TransactionalMethodInDomain {
  @Transactional
  public void run() {}
}
