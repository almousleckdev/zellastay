package archfixtures.clean.shop.domain;

import com.zellastay.shared.ErrorCategory;
import com.zellastay.shared.ErrorCode;

public enum ShopErrorCode implements ErrorCode {
  OUT_OF_STOCK;

  @Override
  public String code() {
    return "shop.out-of-stock";
  }

  @Override
  public ErrorCategory category() {
    return ErrorCategory.BUSINESS_RULE;
  }
}
