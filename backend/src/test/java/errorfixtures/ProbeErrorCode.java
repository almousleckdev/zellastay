package errorfixtures;

import com.zellastay.shared.ErrorCategory;
import com.zellastay.shared.ErrorCode;
import java.util.Locale;

/** An error code whose value is derived from its category, one per category. */
public record ProbeErrorCode(ErrorCategory category) implements ErrorCode {

  @Override
  public String code() {
    return "probe." + category.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
