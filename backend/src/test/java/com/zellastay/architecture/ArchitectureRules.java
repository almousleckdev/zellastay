package com.zellastay.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

final class ArchitectureRules {

  private ArchitectureRules() {}

  static final String MEDIA_SDK_PACKAGE = "com.cloudinary..";

  static Map<String, ArchRule> forBasePackage(String base) {
    return forBasePackage(base, MEDIA_SDK_PACKAGE);
  }

  static Map<String, ArchRule> forBasePackage(String base, String mediaSdkPackage) {
    String domain = base + "..domain..";
    String application = base + "..application..";
    String infrastructure = base + "..infrastructure..";
    String web = base + "..web..";
    String api = base + "..api..";

    Map<String, ArchRule> rules = new LinkedHashMap<>();

    rules.put(
        "domain is independent of frameworks and outer layers",
        noClasses()
            .that()
            .resideInAPackage(domain)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", application, infrastructure, web)
            .allowEmptyShould(true));

    rules.put(
        "application does not depend on web or infrastructure",
        noClasses()
            .that()
            .resideInAPackage(application)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(web, infrastructure)
            .allowEmptyShould(true));

    rules.put(
        "infrastructure does not depend on web",
        noClasses()
            .that()
            .resideInAPackage(infrastructure)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(web)
            .allowEmptyShould(true));

    rules.put(
        "web does not touch infrastructure or repositories",
        noClasses()
            .that()
            .resideInAPackage(web)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(infrastructure)
            .orShould()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true));

    rules.put(
        "published api does not depend on infrastructure or web",
        noClasses()
            .that()
            .resideInAPackage(api)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(infrastructure, web)
            .allowEmptyShould(true));

    rules.put(
        "transactions are demarcated in application services only",
        noClasses()
            .that()
            .resideOutsideOfPackage(application)
            .should()
            .beAnnotatedWith(Transactional.class)
            .allowEmptyShould(true));

    rules.put(
        "transactional methods live in application services only",
        noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage(application)
            .should()
            .beAnnotatedWith(Transactional.class)
            .allowEmptyShould(true));

    rules.put(
        "controllers live in web packages",
        classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .or()
            .areAnnotatedWith(Controller.class)
            .should()
            .resideInAPackage(web)
            .allowEmptyShould(true));

    rules.put(
        "time comes from an injected Clock",
        noClasses()
            .should()
            .callMethod(Instant.class, "now")
            .orShould()
            .callMethod(LocalDate.class, "now")
            .orShould()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callMethod(OffsetDateTime.class, "now")
            .orShould()
            .callMethod(ZonedDateTime.class, "now")
            .orShould()
            .callMethod(System.class, "currentTimeMillis")
            .allowEmptyShould(true));

    rules.put("no standard streams", GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS);
    rules.put("no field injection", GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION);
    rules.put("no java.util.logging", GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING);
    rules.put(
        "no generic exceptions thrown",
        GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS);

    rules.putAll(ErrorHandlingRules.forBasePackage(base));
    rules.put(
        "media provider SDK is confined to media infrastructure",
        ErrorHandlingRules.vendorSdkOnlyInMediaInfrastructure(base, mediaSdkPackage));

    return rules;
  }

  static ArchRule modulesAreFreeOfCycles(String base) {
    return slices().matching(base + ".(*)..").should().beFreeOfCycles().allowEmptyShould(true);
  }
}
