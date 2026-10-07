package com.kenyarealestate.viewing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class LocalProfileGuardTest {

    @Test
    void theLocalProfileCannotRunInProduction() {
        assertThatThrownBy(() -> new LocalProfileGuard("production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ddl-auto: update");
        assertThatThrownBy(() -> new LocalProfileGuard(" Production ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anyOtherEnvironmentIsFine() {
        new LocalProfileGuard("development");
        new LocalProfileGuard(null);
        new LocalProfileGuard("staging");
    }

    @Test
    void theGuardOnlyExistsWhenTheLocalProfileIsActive() {
        assertThat(LocalProfileGuard.class.getAnnotation(Profile.class).value()).containsExactly("local");
    }

    /** The one profile file that enables ddl-auto: update says why it must stay a developer setting. */
    @Test
    void theLocalYamlWarnsThatDdlUpdateIsDevelopmentOnly() throws Exception {
        Path yaml = Path.of("src/main/resources/application-local.yaml");
        String text = Files.readString(yaml);
        assertThat(text).contains("ddl-auto: update");
        assertThat(text).contains("LocalProfileGuard");
    }

    /** Production's own profile must never ask Hibernate to change the schema. */
    @Test
    void theDefaultProfileValidatesInsteadOfUpdating() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/application.yaml"));
        assertThat(text).contains("ddl-auto: validate");
        assertThat(text).doesNotContain("ddl-auto: update");
    }
}
