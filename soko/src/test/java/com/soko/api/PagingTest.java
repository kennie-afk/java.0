package com.soko.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PagingTest {

    @Test
    void emptyOrBlankSearchMatchesEverything() {
        assertThat(Paging.like(null)).isEqualTo("%");
        assertThat(Paging.like("   ")).isEqualTo("%");
    }

    @Test
    void searchIsLowerCasedAndWildcardsAreEscaped() {
        assertThat(Paging.like("  MiLK ")).isEqualTo("%milk%");
        assertThat(Paging.like("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
    }

    @Test
    void limitAndPageAreClamped() {
        assertThat(Paging.limit(0)).isEqualTo(1);
        assertThat(Paging.limit(10_000)).isEqualTo(Paging.MAX_LIMIT);
        assertThat(Paging.page(-3)).isZero();
    }

    @Test
    void aTruncatedListSaysSoAndAFullOneDoesNot() {
        var truncated = Paging.respond(List.of(1, 2), 5, 0, 2);
        assertThat(truncated.getHeaders().getFirst("X-Total-Count")).isEqualTo("5");
        assertThat(truncated.getHeaders().getFirst("X-Has-More")).isEqualTo("true");

        var last = Paging.respond(List.of(5), 5, 2, 2);
        assertThat(last.getHeaders().getFirst("X-Has-More")).isEqualTo("false");

        var exact = Paging.respond(List.of(1, 2), 2, 0, 2);
        assertThat(exact.getHeaders().getFirst("X-Has-More")).isEqualTo("false");
    }
}
