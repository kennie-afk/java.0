package com.smartseason.task.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReferenceCheckerTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    @SuppressWarnings("unchecked")
    private final TypedQuery<Long> query = mock(TypedQuery.class);
    private final ReferenceChecker checker = new ReferenceChecker();
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(checker, "entityManager", entityManager);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("an id that exists in the caller's tenant is accepted")
    void accepted() {
        when(query.getSingleResult()).thenReturn(1L);
        assertThatCode(() -> checker.require("Thing", "thingId", UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an id that is not in the caller's tenant is refused like a missing one")
    void refused() {
        when(query.getSingleResult()).thenReturn(0L);
        assertThatThrownBy(() -> checker.require("Thing", "thingId", UUID.randomUUID()))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("thingId");
    }

    @Test
    @DisplayName("an optional reference left empty is not checked")
    void optionalEmpty() {
        assertThatCode(() -> checker.require("Thing", "thingId", null)).doesNotThrowAnyException();
        assertThat(ReferenceChecker.disabled()).isNotNull();
    }

    @Test
    @DisplayName("the query is filtered by the caller's tenant")
    void queryCarriesTenant() {
        when(query.getSingleResult()).thenReturn(1L);
        checker.require("Thing", "thingId", UUID.randomUUID());
        org.mockito.Mockito.verify(query).setParameter("tenant", tenant);
    }

    @Test
    @DisplayName("something that is not an entity name never reaches the query")
    void entityNameIsConstrained() {
        assertThatThrownBy(() -> checker.require("Thing e where 1=1 or e", "thingId", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
