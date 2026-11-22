package errorfixtures;

import com.zellastay.shared.ErrorCode;
import com.zellastay.shared.ZellaStayException;
import java.io.Serial;

/** A domain exception that carries an internal message the client must never see. */
public class ProbeException extends ZellaStayException {

  public static final String INTERNAL_MESSAGE = "internal debug detail 42 table=secrets";

  @Serial private static final long serialVersionUID = 1L;

  public ProbeException(ErrorCode code) {
    super(code, INTERNAL_MESSAGE);
  }
}
