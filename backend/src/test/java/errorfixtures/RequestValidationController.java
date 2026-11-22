package errorfixtures;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Routes that reject malformed or invalid requests. */
@RestController
public class RequestValidationController {

  @PostMapping("/probe/validate")
  void validateBody(@Valid @RequestBody ProbeRequestBody body) {}

  @GetMapping("/probe/param")
  void requireNumber(@RequestParam int x) {}
}
