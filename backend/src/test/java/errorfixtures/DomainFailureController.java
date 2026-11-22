package errorfixtures;

import com.zellastay.shared.CommonErrorCode;
import com.zellastay.shared.ErrorCategory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Routes that fail with domain exceptions. */
@RestController
public class DomainFailureController {

  @GetMapping("/probe/domain/{category}")
  void failWithCategory(@PathVariable ErrorCategory category) {
    throw new ProbeException(new ProbeErrorCode(category));
  }

  @GetMapping("/probe/not-found")
  void failNotFound() {
    throw new ProbeException(CommonErrorCode.RESOURCE_NOT_FOUND);
  }
}
