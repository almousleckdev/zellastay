package com.zellastay.audit.infrastructure;

import com.zellastay.audit.domain.RequestMetadata;
import com.zellastay.audit.domain.RequestMetadataProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class ServletRequestMetadataProvider implements RequestMetadataProvider {

  private static final String REQUEST_ID = "requestId";

  @Override
  public RequestMetadata current() {
    String requestId = MDC.get(REQUEST_ID);
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servlet) {
      HttpServletRequest request = servlet.getRequest();
      return new RequestMetadata(
          request.getRemoteAddr(), request.getHeader("User-Agent"), requestId);
    }
    return new RequestMetadata(null, null, requestId);
  }
}
