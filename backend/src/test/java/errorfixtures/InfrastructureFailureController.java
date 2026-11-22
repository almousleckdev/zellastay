package errorfixtures;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Routes that fail like a broken dependency, with messages full of secrets and SQL. */
@RestController
public class InfrastructureFailureController {

  @GetMapping("/probe/boom")
  void failUnexpectedly() {
    throw new IllegalStateException("jdbc:postgresql://admin:hunter2@db/zs failed");
  }

  @GetMapping("/probe/integrity")
  void failIntegrity() {
    throw new DataIntegrityViolationException(
        "duplicate key violates uq_secret_email Detail: Key (email)=(a@b.c) already exists");
  }

  @GetMapping("/probe/optimistic")
  void failOptimisticLock() {
    throw new OptimisticLockingFailureException("stale row version 7");
  }

  @GetMapping("/probe/transient")
  void failTransiently() {
    throw new QueryTimeoutException("timeout on select * from secrets");
  }
}
