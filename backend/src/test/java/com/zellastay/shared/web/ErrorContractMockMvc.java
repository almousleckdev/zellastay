package com.zellastay.shared.web;

import java.util.Locale;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Assembles a MockMvc around the real {@link ApiExceptionHandler} and {@link RequestIdFilter}, so
 * the error contract can be tested against the given controllers without starting the application.
 */
final class ErrorContractMockMvc {

  private ErrorContractMockMvc() {}

  static MockMvc around(Object... controllers) {
    ProblemFactory problems = new ProblemFactory(messages());
    return MockMvcBuilders.standaloneSetup(controllers)
        .setControllerAdvice(new ApiExceptionHandler(problems, new ValidationProblems(problems)))
        .addFilters(new RequestIdFilter())
        .setValidator(validator())
        .setLocaleResolver(frenchByDefault())
        .build();
  }

  private static ResourceBundleMessageSource messages() {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages/errors");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    return messages;
  }

  private static LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return validator;
  }

  private static AcceptHeaderLocaleResolver frenchByDefault() {
    AcceptHeaderLocaleResolver locales = new AcceptHeaderLocaleResolver();
    locales.setDefaultLocale(Locale.FRENCH);
    return locales;
  }
}
