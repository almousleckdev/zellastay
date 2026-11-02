package com.zellastay.audit;

import static com.zellastay.support.ApiClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.audit.api.AuditSubject;
import com.zellastay.identity.api.AdminRole;
import com.zellastay.support.AdminFixtures;
import com.zellastay.support.AdminFixtures.Admin;
import com.zellastay.support.ApiClient;
import com.zellastay.support.ApiClient.Response;
import com.zellastay.support.Auth;
import com.zellastay.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class AuditViewerIT {

  private static final String PATH = "/api/v1/admin/audit-events";

  @Value("${local.server.port}")
  int port;

  @Autowired AdminFixtures admins;
  @Autowired AuditRecorder recorder;

  ApiClient api;

  @BeforeEach
  void client() {
    api = new ApiClient(port);
  }

  private void record(UUID actor, TestAuditAction action) {
    recorder.recordBestEffort(
        new AuditEntry(
            AuditActor.admin(actor),
            action,
            new AuditSubject("account", UUID.randomUUID().toString()),
            AuditOutcome.SUCCESS,
            action == TestAuditAction.LOGIN_SUCCESS ? Map.of("method", "password") : Map.of()));
  }

  private Auth.Tokens reader() {
    return admins.login(api, admins.create(AdminRole.SUPER_ADMIN));
  }

  @Test
  void anAdminWithAuditReadSeesEntriesNewestFirstWithCursorPaging() {
    UUID actor = UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      record(actor, TestAuditAction.LOGIN_SUCCESS);
    }
    Auth.Tokens tokens = reader();

    Response first =
        api.get(PATH + "?actorId=" + actor + "&limit=2", "Authorization", bearer(tokens.access()));
    String cursor = first.json().path("nextCursor").asString();
    Response second =
        api.get(
            PATH + "?actorId=" + actor + "&limit=2&cursor=" + cursor,
            "Authorization",
            bearer(tokens.access()));

    assertThat(first.status()).isEqualTo(200);
    assertThat(first.json().path("items").size()).isEqualTo(2);
    JsonNode item = first.json().path("items").get(0);
    assertThat(item.path("action").asString()).isEqualTo("auth.login.success");
    assertThat(item.path("details").path("method").asString()).isEqualTo("password");
    assertThat(item.path("actorId").asString()).isEqualTo(actor.toString());
    assertThat(second.json().path("items").size()).isEqualTo(1);
    assertThat(
            second.json().path("nextCursor").isNull()
                || second.json().path("nextCursor").isMissingNode())
        .isTrue();
  }

  @Test
  void filtersByActionAndRejectsBadParameters() {
    UUID actor = UUID.randomUUID();
    record(actor, TestAuditAction.LOGIN_SUCCESS);
    record(actor, TestAuditAction.ROLE_GRANTED);
    Auth.Tokens tokens = reader();

    Response filtered =
        api.get(
            PATH + "?actorId=" + actor + "&action=admin.role.granted",
            "Authorization",
            bearer(tokens.access()));
    Response badLimit = api.get(PATH + "?limit=0", "Authorization", bearer(tokens.access()));
    Response badCursor =
        api.get(PATH + "?cursor=nonsense", "Authorization", bearer(tokens.access()));
    Response badActor =
        api.get(PATH + "?actorId=not-a-uuid", "Authorization", bearer(tokens.access()));

    assertThat(filtered.json().path("items").size()).isEqualTo(1);
    assertThat(badLimit.status()).isEqualTo(400);
    assertThat(badLimit.code()).isEqualTo("audit.query-invalid");
    assertThat(badCursor.status()).isEqualTo(400);
    assertThat(badActor.status()).isEqualTo(400);
  }

  @Test
  void adminsWithoutAuditReadAnonymousAndOtherAudiencesAreRefused() {
    Admin support = admins.create(AdminRole.SUPPORT_AGENT);
    Auth.Tokens tokens = admins.login(api, support);

    Response denied = api.get(PATH, "Authorization", bearer(tokens.access()));
    Response anonymous = api.get(PATH);

    assertThat(denied.status()).isEqualTo(403);
    assertThat(denied.code()).isEqualTo("auth.forbidden");
    assertThat(anonymous.status()).isEqualTo(401);
  }
}
