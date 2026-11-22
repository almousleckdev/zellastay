package archfixtures.clean.shop.domain;

import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

public class OutOfStockException extends ZellaStayException {
  @Serial private static final long serialVersionUID = 1L;

  public OutOfStockException() {
    super(ShopErrorCode.OUT_OF_STOCK, "no stock left");
  }
}
