package com.zellastay.identity.application;

import java.util.Locale;
import java.util.Set;

/** French is the default language; English is the only other supported one. */
public final class SupportedLocale {

  public static final String DEFAULT = "fr";
  private static final Set<String> SUPPORTED = Set.of("fr", "en");

  private SupportedLocale() {}

  public static String normalize(String requested) {
    if (requested == null) {
      return DEFAULT;
    }
    String language = requested.strip().toLowerCase(Locale.ROOT);
    int separator = language.indexOf('-');
    String base = separator > 0 ? language.substring(0, separator) : language;
    return SUPPORTED.contains(base) ? base : DEFAULT;
  }
}
