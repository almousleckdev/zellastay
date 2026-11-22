package errorfixtures;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** A request body with two constraints, so validation failures can be provoked on demand. */
public record ProbeRequestBody(@NotBlank String name, @Min(1) int count) {}
