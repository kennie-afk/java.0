package com.kenyarealestate.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kenyarealestate.review.dto.ReviewResponse;
import com.kenyarealestate.review.ratelimit.RateLimitFilter;
import com.kenyarealestate.review.ratelimit.RateLimitProperties;
import com.kenyarealestate.review.ratelimit.TokenBucketLimiter;
import com.kenyarealestate.review.security.JwtAuthenticationFilter;
import com.kenyarealestate.review.security.JwtUtil;
import com.kenyarealestate.review.security.SecurityConfig;
import com.kenyarealestate.review.service.ReviewService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The review routes behind the real security configuration: who may hide a review, who may
 * write one, and that a role header nobody signed is not believed.
 */
@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class, RateLimitProperties.class})
@TestPropertySource(properties = {"gateway.signing-secret=test-gateway-signing-secret", "rate-limit.enabled=false"})
class ReviewControllerSecurityTest {

    private static final String SECRET = "test-gateway-signing-secret";

    @Autowired MockMvc mvc;
    @MockitoBean ReviewService service;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean TokenBucketLimiter limiter;

    /** The same HMAC the gateway applies to the identity it forwards. */
    private static String sign(String email, String role, UUID user) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(
                mac.doFinal((email + ":" + role + ":" + user).getBytes(StandardCharsets.UTF_8)));
    }

    private static MockHttpServletRequestBuilder asGateway(MockHttpServletRequestBuilder request, String role, UUID user)
            throws Exception {
        String email = role.toLowerCase() + "@example.test";
        return request.header("X-Auth-Email", email).header("X-Auth-Role", role)
                .header("X-Auth-UserId", user.toString()).header("X-Auth-Signature", sign(email, role, user));
    }

    private static final String VALID_REVIEW = """
            {"sellerId":"%s","propertyId":"%s","paymentId":"%s","rating":5,"comment":"Genuine"}
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    // ---- admin routes -----------------------------------------------------------------------

    @Test
    void hidingAReviewNeedsAnAdmin() throws Exception {
        UUID review = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        when(service.adminHide(eq(review), eq(admin), any())).thenReturn(ReviewResponse.builder().id(review).build());

        mvc.perform(put("/api/reviews/admin/{id}/hide", review)).andExpect(status().is4xxClientError());
        mvc.perform(asGateway(put("/api/reviews/admin/{id}/hide", review), "BUYER", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(asGateway(put("/api/reviews/admin/{id}/hide", review), "SELLER", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verify(service, never()).adminHide(any(), any(), any());

        mvc.perform(asGateway(put("/api/reviews/admin/{id}/hide", review), "ADMIN", admin))
                .andExpect(status().isOk());
        verify(service).adminHide(eq(review), eq(admin), any());
    }

    @Test
    void theHideReasonDefaultsAndTheAdminIsTheOneWhoSignedIn() throws Exception {
        UUID review = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        when(service.adminHide(any(), any(), any())).thenReturn(ReviewResponse.builder().id(review).build());

        mvc.perform(asGateway(put("/api/reviews/admin/{id}/hide", review), "ADMIN", admin)).andExpect(status().isOk());
        mvc.perform(asGateway(put("/api/reviews/admin/{id}/hide", review).param("reason", "Fake"), "ADMIN", admin))
                .andExpect(status().isOk());

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(service, org.mockito.Mockito.times(2)).adminHide(eq(review), eq(admin), reason.capture());
        assertThat(reason.getAllValues()).containsExactly("Reported as fake or abusive", "Fake");
    }

    @Test
    void aRoleHeaderWithoutAValidSignatureIsNotBelieved() throws Exception {
        // Anyone who can reach the pod could type X-Auth-Role: ADMIN; only the gateway can sign it.
        mvc.perform(put("/api/reviews/admin/{id}/hide", UUID.randomUUID())
                        .header("X-Auth-Email", "mallory@example.test").header("X-Auth-Role", "ADMIN")
                        .header("X-Auth-UserId", UUID.randomUUID().toString()).header("X-Auth-Signature", "forged"))
                .andExpect(status().is4xxClientError());
        verify(service, never()).adminHide(any(), any(), any());
    }

    @Test
    void adminListsAndStatsAreAdminOnlyAndTheListFilterReachesTheService() throws Exception {
        when(service.adminGetAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/reviews/admin/all")).andExpect(status().is4xxClientError());
        mvc.perform(asGateway(get("/api/reviews/admin/all"), "BUYER", UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(asGateway(get("/api/reviews/admin/stats"), "BUYER", UUID.randomUUID())).andExpect(status().isForbidden());

        mvc.perform(asGateway(get("/api/reviews/admin/all").param("visible", "false"), "ADMIN", UUID.randomUUID()))
                .andExpect(status().isOk());
        verify(service).adminGetAll(eq(false), any(Pageable.class));
    }

    // ---- public reads and writes ------------------------------------------------------------

    @Test
    void readingReviewsNeedsNoLogin() throws Exception {
        UUID property = UUID.randomUUID();
        when(service.getByProperty(eq(property), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/reviews/property/{id}", property)).andExpect(status().isOk());
    }

    @Test
    void writingAReviewNeedsALoginAndUsesTheSignedInIdentityNotOneFromTheBody() throws Exception {
        UUID buyer = UUID.randomUUID();
        when(service.create(any(), any(), any())).thenReturn(ReviewResponse.builder().id(UUID.randomUUID()).build());

        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(VALID_REVIEW))
                .andExpect(status().is4xxClientError());
        verify(service, never()).create(any(), any(), any());

        mvc.perform(asGateway(post("/api/reviews"), "BUYER", buyer).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REVIEW))
                .andExpect(status().isCreated());
        verify(service).create(eq(buyer), any(), any());
    }

    @Test
    void aReviewWithABadRatingOrNoPaymentIsRejectedBeforeTheServiceSeesIt() throws Exception {
        UUID buyer = UUID.randomUUID();
        for (String body : new String[] {
                VALID_REVIEW.replace("\"rating\":5", "\"rating\":6"),
                VALID_REVIEW.replace("\"rating\":5", "\"rating\":0"),
                VALID_REVIEW.replaceAll("\"paymentId\":\"[^\"]+\",", "")}) {
            mvc.perform(asGateway(post("/api/reviews"), "BUYER", buyer).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
        verify(service, never()).create(any(), any(), any());
    }

    @Test
    void pageSizeIsCappedAtOneHundredForPublicListsAndOneThousandForAdmins() throws Exception {
        mvc.perform(get("/api/reviews/property/{id}", UUID.randomUUID()).param("size", "101"))
                .andExpect(status().is4xxClientError());
        mvc.perform(asGateway(get("/api/reviews/admin/all").param("size", "1001"), "ADMIN", UUID.randomUUID()))
                .andExpect(status().is4xxClientError());
    }
}
