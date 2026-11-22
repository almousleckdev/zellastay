package com.zellastay.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

final class StructureRules {

  static final int MAX_CONSTRUCTOR_DEPENDENCIES = 10;

  private StructureRules() {}

  static Map<String, ArchRule> forBasePackage(String base) {
    Map<String, ArchRule> rules = new LinkedHashMap<>();

    rules.put(
        "a service takes at most " + MAX_CONSTRUCTOR_DEPENDENCIES + " constructor dependencies",
        classes()
            .that(areInjectedLayers(base))
            .should(haveFewConstructorDependencies())
            .allowEmptyShould(true));

    rules.put(
        "nested types are not public, except in configuration properties",
        classes()
            .that(areNestedTypes())
            .and(areNotInConfigurationProperties())
            .should(notBePublic())
            .allowEmptyShould(true));

    rules.put(
        "nesting is one level deep",
        classes().that(areNestedTypes()).should(notBeNestedInANestedType()).allowEmptyShould(true));

    return rules;
  }

  private static DescribedPredicate<JavaClass> areInjectedLayers(String base) {
    return JavaClass.Predicates.resideInAnyPackage(
            base + "..application..", base + "..web..", base + "..infrastructure..")
        .and(DescribedPredicate.not(JavaClass.Predicates.assignableTo(Record.class)))
        .as("classes in application, web or infrastructure");
  }

  private static DescribedPredicate<JavaClass> areNestedTypes() {
    return new DescribedPredicate<>("nested types") {
      @Override
      public boolean test(JavaClass type) {
        return type.getEnclosingClass().isPresent() && !type.isAnonymousClass();
      }
    };
  }

  private static DescribedPredicate<JavaClass> areNotInConfigurationProperties() {
    return new DescribedPredicate<>("not declared in configuration properties") {
      @Override
      public boolean test(JavaClass type) {
        return type.getEnclosingClass()
            .map(outer -> !outer.isAnnotatedWith(ConfigurationProperties.class))
            .orElse(true);
      }
    };
  }

  private static ArchCondition<JavaClass> haveFewConstructorDependencies() {
    return new ArchCondition<>("take few constructor dependencies") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        type.getConstructors().stream()
            .filter(c -> c.getRawParameterTypes().size() > MAX_CONSTRUCTOR_DEPENDENCIES)
            .forEach(
                c ->
                    events.add(
                        SimpleConditionEvent.violated(
                            type,
                            type.getName()
                                + " takes "
                                + c.getRawParameterTypes().size()
                                + " constructor parameters (limit "
                                + MAX_CONSTRUCTOR_DEPENDENCIES
                                + ")")));
      }
    };
  }

  private static ArchCondition<JavaClass> notBePublic() {
    return new ArchCondition<>("not be public") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        if (type.getModifiers().contains(JavaModifier.PUBLIC)) {
          events.add(
              SimpleConditionEvent.violated(
                  type, type.getName() + " is a public nested type; make it a top-level type"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notBeNestedInANestedType() {
    return new ArchCondition<>("not be nested inside a nested type") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        boolean deep =
            type.getEnclosingClass().map(o -> o.getEnclosingClass().isPresent()).orElse(false);
        if (deep) {
          events.add(
              SimpleConditionEvent.violated(
                  type, type.getName() + " is nested more than one level"));
        }
      }
    };
  }
}
