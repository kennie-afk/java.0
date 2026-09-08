package com.smartseason.weather.web;

import com.smartseason.weather.platform.PageResponse;
import com.smartseason.weather.service.WeatherAlertRecordService;
import com.smartseason.weather.web.dto.WeatherAlertRecordCreateRequest;
import com.smartseason.weather.web.dto.WeatherAlertRecordResponse;
import com.smartseason.weather.web.dto.WeatherAlertRecordUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather/v1/weather-alerts")
@Tag(name = "WeatherAlertRecord", description = "Weather feeds, forecasts, NDVI, agro-climatic alerts per geo cell")
public class WeatherAlertRecordController {

    private final WeatherAlertRecordService service;

    public WeatherAlertRecordController(WeatherAlertRecordService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List weather-alerts for the caller's tenant")
    public PageResponse<WeatherAlertRecordResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "Fetch a single WeatherAlertRecord by id")
    public WeatherAlertRecordResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMIST')")
    @Operation(summary = "Create a WeatherAlertRecord")
    public ResponseEntity<WeatherAlertRecordResponse> create(@Valid @RequestBody WeatherAlertRecordCreateRequest request) {
        WeatherAlertRecordResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/weather/v1/weather-alerts/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMIST')")
    @Operation(summary = "Apply a partial update to a WeatherAlertRecord")
    public WeatherAlertRecordResponse update(@PathVariable UUID id, @Valid @RequestBody WeatherAlertRecordUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a WeatherAlertRecord")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
