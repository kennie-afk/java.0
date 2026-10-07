package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Refresh-token rotation, reuse detection, logout, and ending sessions on password change. */
class SessionsTest extends IntegrationTest {

    private JsonNode loginFull(String email, String password) throws Exception {
        return sendJson(post("/v1/auth/login", null), Map.of("email", email, "password", password), 200);
    }

    private JsonNode refresh(String refreshToken, int status) throws Exception {
        return sendJson(post("/v1/auth/refresh", null), Map.of("refreshToken", refreshToken), status);
    }

    private String scalar(String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, OWNER, OWNER_PASSWORD); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void refreshRotatesAndAUsedTokenRevokesTheWholeFamily() throws Exception {
        Org org = newOrg("sess");
        JsonNode first = loginFull(org.email(), org.password());
        String r1 = first.get("refreshToken").asText();
        assertThat(r1).startsWith("r1.");
        assertThat(first.get("expiresInSeconds").asLong()).isEqualTo(15 * 60);

        // Stored only as a hash.
        assertThat(scalar("SELECT count(*) FROM refresh_tokens WHERE token_hash = digest('" + r1 + "', 'sha256')")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM refresh_tokens WHERE encode(token_hash, 'escape') LIKE '%" + r1.substring(3, 12) + "%'")).isEqualTo("0");

        JsonNode second = refresh(r1, 200);
        String r2 = second.get("refreshToken").asText();
        assertThat(r2).isNotEqualTo(r1);
        // The new access token works.
        assertThat(fetch("/v1/auth/me", second.get("token").asText()).get("practitionerId").asText()).isEqualTo(org.adminId().toString());

        // Using r1 a second time after the grace period is theft: the family dies, the legitimate r2 included.
        asOwner("UPDATE refresh_tokens SET used_at = used_at - interval '1 minute'");
        assertThat(refresh(r1, 401).get("code").asText()).isEqualTo("refresh_reused");
        assertThat(refresh(r2, 401).get("code").asText()).isEqualTo("invalid_refresh");
    }

    @Test
    void twoTabsRefreshingTogetherAreNotTreatedAsTheft() throws Exception {
        Org org = newOrg("grace");
        String r1 = loginFull(org.email(), org.password()).get("refreshToken").asText();
        JsonNode a = refresh(r1, 200);
        JsonNode b = refresh(r1, 200);
        // The late tab gets a working access token but no second refresh token, so the first tab's chain stays the only one.
        assertThat(b.has("refreshToken")).isFalse();
        assertThat(fetch("/v1/auth/me", b.get("token").asText()).get("practitionerId")).isNotNull();
        refresh(a.get("refreshToken").asText(), 200);
    }

    @Test
    void badAndExpiredRefreshTokensAreRefused() throws Exception {
        Org org = newOrg("badref");
        refresh("r1." + "A".repeat(43), 401);
        refresh("not-a-token", 401);
        sendJson(post("/v1/auth/refresh", null), Map.of("refreshToken", ""), 400);
        String r = loginFull(org.email(), org.password()).get("refreshToken").asText();
        asOwner("UPDATE refresh_tokens SET expires_at = now() - interval '1 second'");
        refresh(r, 401);
        // A chain cannot outlive its family limit however often it is used.
        String r2 = loginFull(org.email(), org.password()).get("refreshToken").asText();
        asOwner("UPDATE refresh_tokens SET family_started_at = now() - interval '31 days' WHERE token_hash = digest('" + r2 + "', 'sha256')");
        refresh(r2, 401);
    }

    @Test
    void logoutEndsThatDeviceOnlyAndIsSilentAboutUnknownTokens() throws Exception {
        Org org = newOrg("logout");
        String phone = loginFull(org.email(), org.password()).get("refreshToken").asText();
        String desktop = loginFull(org.email(), org.password()).get("refreshToken").asText();
        sendJson(post("/v1/auth/logout", null), Map.of("refreshToken", phone), 204);
        sendJson(post("/v1/auth/logout", null), Map.of("refreshToken", "r1." + "B".repeat(43)), 204);
        refresh(phone, 401);
        refresh(desktop, 200);
    }

    @Test
    void signOutEverywhereKillsIssuedAccessTokensAndEveryRefreshToken() throws Exception {
        Org org = newOrg("everywhere");
        JsonNode s1 = loginFull(org.email(), org.password());
        JsonNode s2 = loginFull(org.email(), org.password());
        String token1 = s1.get("token").asText();
        fetch("/v1/auth/me", token1);
        Thread.sleep(5);
        send(post("/v1/auth/logout-all", token1), 204);
        send(get("/v1/auth/me", token1), 401);
        send(get("/v1/auth/me", s2.get("token").asText()), 401);
        refresh(s1.get("refreshToken").asText(), 401);
        refresh(s2.get("refreshToken").asText(), 401);
        // Signing in again works at once: the new token was issued after the cut-off.
        String again = login(org.email(), org.password());
        assertThat(fetch("/v1/auth/me", again).get("practitionerId")).isNotNull();
    }

    @Test
    void changingOrResettingAPasswordEndsTheSessions() throws Exception {
        Org org = newOrg("pw");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID nurseId = practitionerIdOf(nurse);
        // The nurse's refresh token, and then an administrator resets the nurse's password.
        String nurseEmail = scalar("SELECT email FROM practitioners WHERE id = '" + nurseId + "'");
        String nurseRefresh = loginFull(nurseEmail, "temporary-password-1").get("refreshToken").asText();
        Thread.sleep(5);
        sendJson(post("/v1/staff/" + nurseId + "/reset-password", org.token()), Map.of("temporaryPassword", "another-temporary-pass-2"), 204);
        send(get("/v1/auth/me", nurse), 401);
        refresh(nurseRefresh, 401);
        loginFull(nurseEmail, "another-temporary-pass-2");

        // Own change.
        JsonNode s = loginFull(org.email(), org.password());
        Thread.sleep(5);
        sendJson(post("/v1/auth/me/password", s.get("token").asText()), Map.of("currentPassword", org.password(), "newPassword", "a-brand-new-password-3"), 204);
        send(get("/v1/auth/me", s.get("token").asText()), 401);
        refresh(s.get("refreshToken").asText(), 401);
    }

    @Test
    void aDisabledPersonCannotRefreshAndTheApiRoleCannotReadTheTokenTable() throws Exception {
        Org org = newOrg("disabled");
        String nurse = userWithRole(org, "NURSE", "NURSE");
        UUID nurseId = practitionerIdOf(nurse);
        String email = scalar("SELECT email FROM practitioners WHERE id = '" + nurseId + "'");
        String r = loginFull(email, "temporary-password-1").get("refreshToken").asText();
        send(post("/v1/staff/" + nurseId + "/disable", org.token()), 200);
        refresh(r, 401);
        try (Connection c = DriverManager.getConnection(DB_URL, "hms_app", "apppw-test"); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.executeQuery("SELECT * FROM refresh_tokens")).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }
}
