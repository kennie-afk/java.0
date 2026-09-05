package com.smartseason.weather.web;

import com.smartseason.weather.platform.PageResponse;
import com.smartseason.weather.service.ForecastService;
import com.smartseason.weather.web.dto.ForecastCreateRequest;
import com.smartseason.weather.web.dto.ForecastResponse;
import com.smartseason.weather.web.dto.ForecastUpdateRequest;
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
@RequestMapping("/api/weather/v1/forecasts")
@Tag(name = "Forecast", description = "Weather feeds, forecasts, NDVI, agro-climatic alerts per geo cell")
public class ForecastController {

    private final ForecastService service;

    public ForecastController(ForecastService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List forecasts for the caller's tenant")
    public PageResponse<ForecastResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single Forecast by id")
    public ForecastResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a Forecast")
    public ResponseEntity<ForecastResponse> create(@Valid @RequestBody ForecastCreateRequest request) {
        ForecastResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/weather/v1/forecasts/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a Forecast")
    public ForecastResponse update(@PathVariable UUID id, @Valid @RequestBody ForecastUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a Forecast")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
