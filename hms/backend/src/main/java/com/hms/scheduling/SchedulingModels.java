package com.hms.scheduling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

final class SchedulingModels {
    private SchedulingModels() {}

    static final String PRIORITY = "EMERGENCY|PRIORITY|ROUTINE";

    record SessionInput(@NotNull UUID practitionerId, @NotNull @Min(1) @Max(7) Integer weekday,
                        @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}

    record ClinicInput(@NotNull UUID facilityId, @NotBlank @Size(min = 2, max = 120) String name, @Size(max = 80) String specialty,
                       @Min(5) @Max(120) Integer slotMinutes, List<@Valid SessionInput> sessions) {}

    record ClinicUpdate(@NotBlank @Size(min = 2, max = 120) String name, @Size(max = 80) String specialty,
                        @Min(5) @Max(120) Integer slotMinutes, @NotNull Boolean active, @NotNull List<@Valid SessionInput> sessions) {}

    record Session(UUID id, UUID practitionerId, int weekday, LocalTime startTime, LocalTime endTime) {}

    record Clinic(UUID id, UUID facilityId, String name, String specialty, int slotMinutes, boolean active, List<Session> sessions) {}

    record Slot(Instant start, Instant end, UUID practitionerId) {}

    record BookInput(@NotNull UUID clinicId, @NotNull UUID patientId, @NotNull UUID practitionerId, @NotNull Instant startsAt,
                     @Size(max = 300) String reason) {}

    record WalkInInput(@NotNull UUID clinicId, @NotNull UUID patientId, UUID practitionerId,
                       @Pattern(regexp = PRIORITY) String priority, @Size(max = 300) String reason) {}

    record RescheduleInput(@NotNull Instant startsAt, @NotNull Integer version) {}

    record CancelInput(@NotBlank @Size(min = 3, max = 300) String reason) {}

    record PriorityInput(@NotNull @Pattern(regexp = PRIORITY) String priority) {}

    record Appointment(UUID id, UUID facilityId, UUID clinicId, UUID patientId, String patientName, UUID practitionerId, Instant startsAt,
                       Instant endsAt, String status, String priority, boolean walkIn, String reason, Integer queueNumber,
                       Instant checkedInAt, String cancelReason, int version) {}
}
