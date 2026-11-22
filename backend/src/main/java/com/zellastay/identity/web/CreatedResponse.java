package com.zellastay.identity.web;

import java.util.UUID;

/** Response carrying the id of a created resource. */
record CreatedResponse(UUID id) {}
