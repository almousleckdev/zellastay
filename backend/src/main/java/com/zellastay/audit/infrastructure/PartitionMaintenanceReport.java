package com.zellastay.audit.infrastructure;

/** What one partition maintenance run created and what it found outside the expected months. */
public record PartitionMaintenanceReport(int created, int stragglers) {}
