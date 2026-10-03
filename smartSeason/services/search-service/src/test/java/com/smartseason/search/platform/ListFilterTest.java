package com.smartseason.search.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListFilterTest {

    enum Status { ACTIVE, CLOSED }

    private static final Map<String, Class<?>> FILTERABLE = Map.of(
            "farmId", UUID.class, "status", Status.class, "irrigated", Boolean.class, "name", String.class);
    private static final List<String> SEARCHABLE = List.of("name");

    private static void build(Map<String, String> params) {
        ListFilter.of(UUID.randomUUID(), params, FILTERABLE, SEARCHABLE);
    }

    @Test
    @DisplayName("paging and sort parameters alone are not a filter")
    void pagingIsNotAFilter() {
        assertThat(ListFilter.isEmpty(Map.of("page", "2", "size", "25", "sort", "name,asc"))).isTrue();
        assertThat(ListFilter.isEmpty(Map.of("q", "  "))).isTrue();
        assertThat(ListFilter.isEmpty(Map.of("q", "maize"))).isFalse();
        assertThat(ListFilter.isEmpty(Map.of("status", "ACTIVE"))).isFalse();
    }

    @Test
    @DisplayName("a field that is not declared filterable is rejected, never ignored")
    void unknownFieldIsRejected() {
        assertThatThrownBy(() -> build(Map.of("tenantId", UUID.randomUUID().toString())))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("Cannot filter by 'tenantId'");
    }

    @Test
    @DisplayName("a malformed value is a rule violation, not a server error")
    void badValueIsRejected() {
        assertThatThrownBy(() -> build(Map.of("farmId", "not-a-uuid"))).isInstanceOf(DomainRuleException.class);
        assertThatThrownBy(() -> build(Map.of("status", "NOPE"))).isInstanceOf(DomainRuleException.class);
        assertThatThrownBy(() -> build(Map.of("irrigated", "maybe"))).isInstanceOf(DomainRuleException.class);
    }

    @Test
    @DisplayName("valid values and enum case are accepted")
    void validValuesPass() {
        assertThatCode(() -> build(Map.of(
                "farmId", UUID.randomUUID().toString(), "status", "active", "irrigated", "TRUE", "q", "maize")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("search text is capped so one caller cannot send a megabyte LIKE")
    void searchTextIsCapped() {
        assertThatThrownBy(() -> build(Map.of("q", "x".repeat(101)))).isInstanceOf(DomainRuleException.class);
    }
}
