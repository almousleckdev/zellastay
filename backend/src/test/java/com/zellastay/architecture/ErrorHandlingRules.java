package com.zellastay.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.zellastay.shared.ZellaStayException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

final class ErrorHandlingRules {

  private ErrorHandlingRules() {}

  static Map<String, ArchRule> forBasePackage(String base) {
    Map<String, ArchRule> rules = new LinkedHashMap<>();

    rules.put(
        "exception handlers exist only in controller advice",
        methods()
            .that()
            .areAnnotatedWith(ExceptionHandler.class)
            .should()
            .beDeclaredInClassesThat()
            .areAnnotatedWith(RestControllerAdvice.class)
            .orShould()
            .beDeclaredInClassesThat()
            .areAnnotatedWith(ControllerAdvice.class)
            .allowEmptyShould(true));

    rules.put(
        "controller advice lives in web packages",
        classes()
            .that()
            .areAnnotatedWith(RestControllerAdvice.class)
            .or()
            .areAnnotatedWith(ControllerAdvice.class)
            .should()
            .resideInAPackage(base + "..web..")
            .allowEmptyShould(true));

    rules.put(
        "controllers never catch exceptions",
        classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should(notContainTryCatchBlocks())
            .allowEmptyShould(true));

    rules.put(
        "application exceptions extend ZellaStayException",
        classes()
            .that()
            .areAssignableTo(Throwable.class)
            .and()
            .resideInAPackage(base + "..")
            .should()
            .beAssignableTo(ZellaStayException.class)
            .allowEmptyShould(true));

    return rules;
  }

  static ArchRule vendorSdkOnlyInMediaInfrastructure(String base, String sdkPackage) {
    return noClasses()
        .that()
        .resideOutsideOfPackage(base + "..media.infrastructure..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(sdkPackage)
        .because("the media provider SDK is an infrastructure detail of the media module")
        .allowEmptyShould(true);
  }

  private static ArchCondition<JavaClass> notContainTryCatchBlocks() {
    return new ArchCondition<>("not contain try/catch blocks") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        type.getMethods().stream()
            .filter(method -> !method.getTryCatchBlocks().isEmpty())
            .forEach(
                method ->
                    events.add(
                        SimpleConditionEvent.violated(
                            method, method.getFullName() + " catches exceptions")));
      }
    };
  }
}
