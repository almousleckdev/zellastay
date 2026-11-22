package com.zellastay.architecture;

import static com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.zellastay.shared.ErrorCategory;
import com.zellastay.shared.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ErrorCatalogTest {

  private static final String BASE = "com.zellastay";
  private static final String BUNDLE = "messages/errors";
  private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z0-9][a-z0-9-]*)+$");
  private static final Set<String> SHARED_NAMESPACES =
      Set.of("request", "resource", "auth", "validation", "data", "service", "rate", "internal");

  private static final ResourceBundle FRENCH = ResourceBundle.getBundle(BUNDLE, Locale.ROOT);
  private static final ResourceBundle ENGLISH = ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH);

  @Test
  void frenchAndEnglishBundlesHaveIdenticalKeysAndNoBlankValues() {
    assertThat(new TreeSet<>(ENGLISH.keySet())).isEqualTo(new TreeSet<>(FRENCH.keySet()));
    FRENCH.keySet().forEach(key -> assertThat(FRENCH.getString(key)).as("fr %s", key).isNotBlank());
    ENGLISH
        .keySet()
        .forEach(key -> assertThat(ENGLISH.getString(key)).as("en %s", key).isNotBlank());
  }

  @Test
  void everyErrorCategoryHasGenericMessagesInBothLanguages() {
    for (ErrorCategory category : ErrorCategory.values()) {
      String base = "error.category." + category.name().toLowerCase(Locale.ROOT);
      assertThat(FRENCH.containsKey(base + ".title")).as(base).isTrue();
      assertThat(FRENCH.containsKey(base + ".detail")).as(base).isTrue();
    }
  }

  @Test
  void everyDeclaredErrorCodeIsWellFormedUniqueNamespacedAndTranslated() {
    List<String> problems = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (ErrorCode code : declaredErrorCodes()) {
      String value = code.code();
      if (!CODE.matcher(value).matches()) {
        problems.add("malformed code " + value);
      }
      if (!seen.add(value)) {
        problems.add("duplicate code " + value);
      }
      for (String part : List.of("title", "detail")) {
        String key = "error." + value + "." + part;
        if (!FRENCH.containsKey(key) || !ENGLISH.containsKey(key)) {
          problems.add("missing fr/en message " + key);
        }
      }
    }

    assertThat(problems).isEmpty();
    assertThat(seen).isNotEmpty();
  }

  @Test
  void codeNamespacesMatchTheOwningModule() {
    List<String> problems = new ArrayList<>();

    for (JavaClass type : errorCodeEnums()) {
      String module = type.getPackageName().split("\\.")[2];
      for (Object constant : type.reflect().getEnumConstants()) {
        String namespace = ((ErrorCode) constant).code().split("\\.")[0];
        boolean allowed =
            module.equals("shared")
                ? SHARED_NAMESPACES.contains(namespace)
                : module.equals(namespace);
        if (!allowed) {
          problems.add(type.getSimpleName() + " has code outside its namespace: " + constant);
        }
      }
    }

    assertThat(problems).isEmpty();
  }

  private static List<JavaClass> errorCodeEnums() {
    return new ClassFileImporter()
        .withImportOption(DO_NOT_INCLUDE_TESTS).importPackages(BASE).stream()
            .filter(type -> type.isEnum() && type.isAssignableTo(ErrorCode.class))
            .toList();
  }

  private static List<ErrorCode> declaredErrorCodes() {
    List<ErrorCode> codes = new ArrayList<>();
    for (JavaClass type : errorCodeEnums()) {
      for (Object constant : type.reflect().getEnumConstants()) {
        codes.add((ErrorCode) constant);
      }
    }
    return codes;
  }
}
