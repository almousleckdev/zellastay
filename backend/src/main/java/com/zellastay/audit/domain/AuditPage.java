package com.zellastay.audit.domain;

import java.util.List;

/** One page, newest first; {@code nextCursor} is null on the last page. */
public record AuditPage(List<AuditRecord> items, String nextCursor) {}
