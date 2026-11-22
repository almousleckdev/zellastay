package com.zellastay.support;

import com.zellastay.shared.security.TokenAudience;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Deny-by-default check: every mapped route must be either a deliberately public endpoint or be
 * covered by a route its audience's security chain explicitly allows.
 */
public final class RouteCatalog {

  static final List<String> ANONYMOUS_PREFIXES =
      List.of("/api/v1/auth/", "/api/v1/vendor/auth/", "/api/v1/admin/auth/", "/api/v1/public/");

  private static final PathPatternParser PARSER = new PathPatternParser();

  private RouteCatalog() {}

  /**
   * @param mapped route patterns the application serves
   * @param allowed audience routes registered by modules
   * @param publicByDesign the explicit allow-list of anonymous routes
   * @param nonApi routes outside the API that are allowed (for example the container error page)
   * @return a description of every route that is not protected
   */
  public static List<String> unprotected(
      Collection<String> mapped,
      Map<TokenAudience, List<String>> allowed,
      Set<String> publicByDesign,
      Set<String> nonApi) {
    List<String> problems = new ArrayList<>();
    for (String route : mapped) {
      if (!route.startsWith("/api/")) {
        if (!nonApi.contains(route)) {
          problems.add(route + " is outside /api and not allow-listed");
        }
      } else if (isAnonymousPrefix(route)) {
        if (!publicByDesign.contains(route)) {
          problems.add(route + " is anonymous but not in the public allow-list");
        }
      } else if (!covered(route, allowed.getOrDefault(audienceOf(route), List.of()))) {
        problems.add(route + " is not registered for " + audienceOf(route));
      }
    }
    return problems;
  }

  private static boolean isAnonymousPrefix(String route) {
    return ANONYMOUS_PREFIXES.stream().anyMatch(route::startsWith);
  }

  private static TokenAudience audienceOf(String route) {
    if (route.startsWith("/api/v1/vendor/")) {
      return TokenAudience.VENDOR;
    }
    if (route.startsWith("/api/v1/admin/")) {
      return TokenAudience.ADMIN;
    }
    return TokenAudience.CUSTOMER;
  }

  private static boolean covered(String route, List<String> patterns) {
    PathContainer sample = PathContainer.parsePath(route.replaceAll("\\{[^/]+}", "x"));
    for (String pattern : patterns) {
      PathPattern parsed = PARSER.parse(pattern);
      if (parsed.matches(sample)) {
        return true;
      }
    }
    return false;
  }
}
