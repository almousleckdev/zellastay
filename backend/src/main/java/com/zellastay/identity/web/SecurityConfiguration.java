package com.zellastay.identity.web;

import com.zellastay.identity.application.BearerTokenAuthenticator;
import com.zellastay.shared.security.AudienceRoutes;
import com.zellastay.shared.security.TokenAudience;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * One filter chain per audience, each ending in deny-all: a route is reachable only when a module
 * registered it through {@link AudienceRoutes}. Authentication endpoints are anonymous by prefix.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration {

  static final String[] ANONYMOUS_PREFIXES = {
    "/api/v1/auth/**", "/api/v1/vendor/auth/**", "/api/v1/admin/auth/**", "/api/v1/public/**"
  };

  @Bean
  @Order(1)
  SecurityFilterChain managementChain(HttpSecurity http) throws Exception {
    http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
    return stateless(http).build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain anonymousChain(HttpSecurity http) throws Exception {
    http.securityMatcher(ANONYMOUS_PREFIXES)
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
    return stateless(http).build();
  }

  @Bean
  @Order(3)
  SecurityFilterChain vendorChain(
      HttpSecurity http, Dependencies dependencies, ObjectProvider<AudienceRoutes> routes)
      throws Exception {
    return audienceChain(http, "/api/v1/vendor/**", TokenAudience.VENDOR, dependencies, routes);
  }

  @Bean
  @Order(4)
  SecurityFilterChain adminChain(
      HttpSecurity http, Dependencies dependencies, ObjectProvider<AudienceRoutes> routes)
      throws Exception {
    String[] patterns = patterns(routes, TokenAudience.ADMIN);
    http.securityMatcher("/api/v1/admin/**")
        .authorizeHttpRequests(
            requests -> {
              if (patterns.length > 0) {
                requests.requestMatchers(patterns).hasAuthority(PrincipalAuthorities.MFA_VERIFIED);
              }
              requests.anyRequest().denyAll();
            });
    return withBearer(stateless(http), TokenAudience.ADMIN, dependencies).build();
  }

  @Bean
  @Order(5)
  SecurityFilterChain customerChain(
      HttpSecurity http, Dependencies dependencies, ObjectProvider<AudienceRoutes> routes)
      throws Exception {
    return audienceChain(http, "/api/v1/**", TokenAudience.CUSTOMER, dependencies, routes);
  }

  /** Everything outside the API (unmapped paths, the container error dispatch) falls through. */
  @Bean
  @Order(6)
  SecurityFilterChain fallbackChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
    return stateless(http).build();
  }

  private SecurityFilterChain audienceChain(
      HttpSecurity http,
      String matcher,
      TokenAudience audience,
      Dependencies dependencies,
      ObjectProvider<AudienceRoutes> routes)
      throws Exception {
    String[] patterns = patterns(routes, audience);
    http.securityMatcher(matcher)
        .authorizeHttpRequests(
            requests -> {
              if (patterns.length > 0) {
                requests.requestMatchers(patterns).authenticated();
              }
              requests.anyRequest().denyAll();
            });
    return withBearer(stateless(http), audience, dependencies).build();
  }

  private static String[] patterns(ObjectProvider<AudienceRoutes> routes, TokenAudience audience) {
    return routes
        .orderedStream()
        .filter(route -> route.audience() == audience)
        .flatMap(route -> route.patterns().stream())
        .toArray(String[]::new);
  }

  private HttpSecurity withBearer(HttpSecurity http, TokenAudience audience, Dependencies d)
      throws Exception {
    http.addFilterBefore(
            new BearerAuthenticationFilter(audience, d.authenticator(), d.authorities()),
            AnonymousAuthenticationFilter.class)
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(d.handlers()).accessDeniedHandler(d.handlers()));
    return http;
  }

  private HttpSecurity stateless(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(cache -> cache.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(Customizer.withDefaults());
  }

  @Bean
  Dependencies securityDependencies(
      BearerTokenAuthenticator authenticator,
      PrincipalAuthorities authorities,
      ProblemSecurityHandlers handlers) {
    return new Dependencies(authenticator, authorities, handlers);
  }

  record Dependencies(
      BearerTokenAuthenticator authenticator,
      PrincipalAuthorities authorities,
      ProblemSecurityHandlers handlers) {}
}
