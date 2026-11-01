package com.zellastay.architecture;

import static com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ArchitectureRulesTest {

  private static final String BASE = "com.zellastay";

  private static final JavaClasses PRODUCTION =
      new ClassFileImporter().withImportOption(DO_NOT_INCLUDE_TESTS).importPackages(BASE);

  @TestFactory
  Stream<DynamicTest> productionCodeObeysEveryRule() {
    Map<String, ArchRule> rules = ArchitectureRules.forBasePackage(BASE);
    return rules.entrySet().stream()
        .map(
            rule ->
                DynamicTest.dynamicTest(rule.getKey(), () -> rule.getValue().check(PRODUCTION)));
  }

  @Test
  void modulesAreFreeOfCycles() {
    ArchitectureRules.modulesAreFreeOfCycles(BASE).check(PRODUCTION);
  }
}
