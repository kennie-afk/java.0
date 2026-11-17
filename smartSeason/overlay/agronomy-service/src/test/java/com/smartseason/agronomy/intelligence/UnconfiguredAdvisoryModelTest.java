package com.smartseason.agronomy.intelligence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class UnconfiguredAdvisoryModelTest {

    @Test
    void diagnosisIsRefusedNotGuessed() {
        DiagnosisRequest request = new DiagnosisRequest("MAIZE", "yellow leaves", null, null, null);
        assertThatThrownBy(() -> new UnconfiguredAdvisoryModel().diagnose(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
