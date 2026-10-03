package com.hms.reporting;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReportDefinitionModels {
    private ReportDefinitionModels() {}

    private static final String UID = "^[A-Za-z][A-Za-z0-9]{10}$";

    /** One data element. dhis2DataElement and dhis2Options (category label to option-combo UID) are only used by the DHIS2 export. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Element(@NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]{0,39}$", message = "letters, digits, . _ -") String code, @NotBlank @Size(min = 2, max = 200) String label,
                          @NotBlank String measure, @Pattern(regexp = "NONE|SEX|AGE_BAND|MODALITY") String disaggregation, @Size(max = 12) @Pattern(regexp = "^[A-Za-z0-9._-]*$") String filter,
                          @Pattern(regexp = UID, message = "an 11 character identifier starting with a letter") String dhis2DataElement, Map<String, String> dhis2Options) {}

    public record DefinitionInput(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_.-]{0,39}$", message = "upper case letters, digits, . _ -") String code,
                                  @NotBlank @Size(min = 2, max = 200) String name, @Size(max = 1000) String description,
                                  @NotEmpty @Size(max = 60) List<@Valid @NotNull Element> elements,
                                  @Pattern(regexp = UID, message = "an 11 character identifier starting with a letter") String dhis2DataSet, Map<UUID, String> dhis2OrgUnits, Boolean active) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Definition(UUID id, String code, String name, String description, List<Element> elements, String dhis2DataSet, Map<UUID, String> dhis2OrgUnits, boolean active) {}

    public record MeasureInfo(String code, String label, String description, String filter, boolean filterRequired, List<String> disaggregations) {}

    public record Cell(String element, String label, String category, long value) {}

    public record RunResult(UUID definitionId, String code, String name, UUID facilityId, String facilityName, LocalDate from, LocalDate to, String timezone, List<Cell> cells, String note) {}
}
