package com.zellastay.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchitectureRulesDetectViolationsTest {

  private static final String VIOLATING = "archfixtures.violating";
  private static final String CLEAN = "archfixtures.clean";
  private static final String SDK = "archfixtures.sdk..";

  private static final JavaClasses VIOLATING_CLASSES =
      new ClassFileImporter().importPackages(VIOLATING);
  private static final JavaClasses CLEAN_CLASSES = new ClassFileImporter().importPackages(CLEAN);

  @Test
  void everyRuleFlagsItsViolatingFixture() {
    Map<String, ArchRule> rules = ArchitectureRules.forBasePackage(VIOLATING, SDK);

    rules.forEach(
        (name, rule) ->
            assertThatThrownBy(() -> rule.check(VIOLATING_CLASSES))
                .as("rule must detect violation: %s", name)
                .isInstanceOf(AssertionError.class));
  }

  @Test
  void cycleRuleFlagsCyclicModules() {
    assertThatThrownBy(
            () -> ArchitectureRules.modulesAreFreeOfCycles(VIOLATING).check(VIOLATING_CLASSES))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void cleanFixturePassesEveryRule() {
    ArchitectureRules.forBasePackage(CLEAN, SDK)
        .forEach(
            (name, rule) ->
                assertThatCode(() -> rule.check(CLEAN_CLASSES))
                    .as("clean code must satisfy rule: %s", name)
                    .doesNotThrowAnyException());
    assertThatCode(() -> ArchitectureRules.modulesAreFreeOfCycles(CLEAN).check(CLEAN_CLASSES))
        .doesNotThrowAnyException();
  }
}
