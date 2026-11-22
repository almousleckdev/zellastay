package errorfixtures;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Routes that fail the way method security does inside a controller. */
@RestController
public class SecurityFailureController {

  @GetMapping("/probe/denied")
  String denied() {
    throw new AccessDeniedException("internal detail");
  }

  @GetMapping("/probe/unauthenticated")
  String unauthenticated() {
    throw new BadCredentialsException("internal detail");
  }
}
