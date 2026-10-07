package com.kenyarealestate.property.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kenyarealestate.property.dto.PropertyResponse;
import com.kenyarealestate.property.dto.PropertySearchRequest;
import com.kenyarealestate.property.exception.GlobalExceptionHandler;
import com.kenyarealestate.property.exception.NotFoundException;
import com.kenyarealestate.property.security.JwtUtil;
import com.kenyarealestate.property.service.PropertyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP contract of the public search and listing routes: which parameters reach the service,
 * what a bad sort or an oversize page does, and who is treated as the owner or an admin.
 */
class PropertyControllerTest {

    private final PropertyService svc = mock(PropertyService.class);
    private final PropertyController controller = new PropertyController(svc, mock(JwtUtil.class));
    private MockMvc mvc;

    @BeforeEach
    void mvc() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static RequestPostProcessor signedInAs(UUID user, String... roles) {
        return request -> {
            request.setAttribute("authenticatedUserId", user);
            for (String role : roles) {
                ((MockHttpServletRequest) request).addUserRole(role);
            }
            return request;
        };
    }

    // ---- search -----------------------------------------------------------------------------

    @Test
    void searchPassesEveryFilterAndTheRequestedSortToTheService() throws Exception {
        when(svc.search(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/properties/search")
                        .param("county", "Nairobi").param("city", "Westlands").param("propertyType", "APARTMENT")
                        .param("listingType", "RENT").param("keyword", "park").param("minPrice", "1000")
                        .param("maxPrice", "9000").param("minBedrooms", "2").param("verifiedOnly", "true")
                        .param("page", "3").param("size", "25").param("sortBy", "price").param("direction", "ASC"))
                .andExpect(status().isOk());

        ArgumentCaptor<PropertySearchRequest> sent = ArgumentCaptor.forClass(PropertySearchRequest.class);
        verify(svc).search(sent.capture());
        PropertySearchRequest req = sent.getValue();
        assertThat(req.getCounty()).isEqualTo("Nairobi");
        assertThat(req.getCity()).isEqualTo("Westlands");
        assertThat(req.getPropertyType()).isEqualTo("APARTMENT");
        assertThat(req.getListingType()).isEqualTo("RENT");
        assertThat(req.getKeyword()).isEqualTo("park");
        assertThat(req.getMinPrice()).isEqualByComparingTo("1000");
        assertThat(req.getMaxPrice()).isEqualByComparingTo("9000");
        assertThat(req.getMinBedrooms()).isEqualTo(2);
        assertThat(req.isVerifiedOnly()).isTrue();
        assertThat(req.getPage()).isEqualTo(3);
        assertThat(req.getSize()).isEqualTo(25);
        assertThat(req.getSortBy()).isEqualTo("price");
        assertThat(req.getDirection()).isEqualTo("ASC");
    }

    @Test
    void searchWithNoParametersAsksForTheNewestTwentyFirst() throws Exception {
        when(svc.search(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/properties/search")).andExpect(status().isOk());

        ArgumentCaptor<PropertySearchRequest> sent = ArgumentCaptor.forClass(PropertySearchRequest.class);
        verify(svc).search(sent.capture());
        assertThat(sent.getValue().getPage()).isZero();
        assertThat(sent.getValue().getSize()).isEqualTo(20);
        assertThat(sent.getValue().getSortBy()).isEqualTo("createdAt");
        assertThat(sent.getValue().getDirection()).isEqualTo("DESC");
        assertThat(sent.getValue().isVerifiedOnly()).isFalse();
    }

    @Test
    void aSortTheSearchDoesNotSupportIsABadRequestThatNamesTheChoices() throws Exception {
        when(svc.search(any())).thenThrow(new IllegalArgumentException(
                "Cannot sort search results by 'sellerId'. Choose one of: bedrooms, createdAt, price, viewCount."));

        mvc.perform(get("/api/properties/search").param("sortBy", "sellerId"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Choose one of")));
    }

    @Test
    void pageSizeLimitsAreDeclaredOnEveryPagedRoute() throws Exception {
        var executable = Validation.buildDefaultValidatorFactory().getValidator().forExecutables();
        Method search = PropertyController.class.getMethod("search", String.class, String.class, String.class,
                String.class, String.class, java.math.BigDecimal.class, java.math.BigDecimal.class, Integer.class,
                boolean.class, int.class, int.class, String.class, String.class);
        Method mine = PropertyController.class.getMethod("myListings", HttpServletRequest.class, int.class,
                int.class, String.class, String.class);
        Method bySeller = PropertyController.class.getMethod("bySeller", UUID.class, int.class, int.class);

        Object[] tooBig = {null, null, null, null, null, null, null, null, false, 0, 1001, "createdAt", "DESC"};
        Object[] negativePage = {null, null, null, null, null, null, null, null, false, -1, 20, "createdAt", "DESC"};
        Object[] fine = {null, null, null, null, null, null, null, null, false, 0, 1000, "createdAt", "DESC"};
        assertThat(executable.validateParameters(controller, search, tooBig)).isNotEmpty();
        assertThat(executable.validateParameters(controller, search, negativePage)).isNotEmpty();
        assertThat(executable.validateParameters(controller, search, fine)).isEmpty();

        assertThat(executable.validateParameters(controller, mine,
                new Object[] {new MockHttpServletRequest(), 0, 101, "createdAt", "DESC"})).isNotEmpty();
        assertThat(executable.validateParameters(controller, bySeller, new Object[] {UUID.randomUUID(), 0, 101}))
                .isNotEmpty();
        assertThat(executable.validateParameters(controller, bySeller, new Object[] {UUID.randomUUID(), 0, 100}))
                .isEmpty();
    }

    // ---- a seller's public page, the owner's list ---------------------------------------------

    @Test
    void aSellersPublicListingsAreBoundedByDefault() throws Exception {
        UUID seller = UUID.randomUUID();
        when(svc.getActiveBySeller(seller, 0, 50)).thenReturn(List.of());

        mvc.perform(get("/api/properties/seller/{id}", seller)).andExpect(status().isOk());

        verify(svc).getActiveBySeller(seller, 0, 50);
    }

    @Test
    void myListingsRefuseAnUnknownSortBeforeAnythingIsQueried() throws Exception {
        mvc.perform(get("/api/properties/my").param("sortBy", "sellerId").with(signedInAs(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Cannot sort by")));
        verify(svc, never()).getMyListings(any(), any());
    }

    @Test
    void myListingsAreScopedToTheSignedInUserAndSortedWithAnIdTiebreak() throws Exception {
        UUID me = UUID.randomUUID();
        when(svc.getMyListings(eq(me), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/properties/my").param("sortBy", "price").param("direction", "ASC").with(signedInAs(me)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(svc).getMyListings(eq(me), page.capture());
        assertThat(page.getValue().getSort().toString()).isEqualTo("price: ASC,id: DESC");
    }

    @Test
    void myListingsWithoutAnIdentityAreRefused() throws Exception {
        mvc.perform(get("/api/properties/my")).andExpect(status().isUnauthorized());
        verify(svc, never()).getMyListings(any(), any());
    }

    @Test
    void adminListingsRefuseAnUnknownSortToo() throws Exception {
        mvc.perform(get("/api/properties/admin/all").param("sortBy", "password"))
                .andExpect(status().isBadRequest());
        verify(svc, never()).adminGetAll(any(), any());
    }

    // ---- who counts as the owner or an admin on GET /{id} -------------------------------------

    @Test
    void anAnonymousVisitorIsPassedToTheServiceAsNobody() throws Exception {
        UUID id = UUID.randomUUID();
        when(svc.getById(eq(id), isNull(), eq(false), any())).thenReturn(new PropertyResponse());

        mvc.perform(get("/api/properties/{id}", id).header("X-Forwarded-For", "41.1.2.3, 10.0.0.1"))
                .andExpect(status().isOk());

        verify(svc).getById(id, null, false, "41.1.2.3");
    }

    @Test
    void aSignedInOwnerIsPassedByIdAndAnAdminIsFlagged() throws Exception {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        when(svc.getById(any(), any(), anyBooleanArg(), any())).thenReturn(new PropertyResponse());

        mvc.perform(get("/api/properties/{id}", id).with(signedInAs(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/properties/{id}", id).with(signedInAs(admin, "ADMIN"))).andExpect(status().isOk());

        verify(svc).getById(eq(id), eq(owner), eq(false), any());
        verify(svc).getById(eq(id), eq(admin), eq(true), any());
    }

    @Test
    void aListingThatIsNotVisibleToTheCallerIsA404NotA403() throws Exception {
        UUID id = UUID.randomUUID();
        when(svc.getById(eq(id), any(), eq(false), any())).thenThrow(new NotFoundException("Property not found"));

        mvc.perform(get("/api/properties/{id}", id)).andExpect(status().isNotFound());
    }

    // ---- publish ----------------------------------------------------------------------------

    @Test
    void publishActsAsTheSignedInSeller() throws Exception {
        UUID id = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        when(svc.publish(seller, id)).thenReturn(new PropertyResponse());

        mvc.perform(put("/api/properties/{id}/publish", id).with(signedInAs(seller))).andExpect(status().isOk());

        verify(svc).publish(seller, id);
    }

    private static boolean anyBooleanArg() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
