package com.zellastay.audit.web;

import com.zellastay.audit.domain.AuditPage;
import java.util.List;

record AuditEventPageView(List<AuditEventView> items, String nextCursor) {

  static AuditEventPageView from(AuditPage page) {
    return new AuditEventPageView(
        page.items().stream().map(AuditEventView::from).toList(), page.nextCursor());
  }
}
