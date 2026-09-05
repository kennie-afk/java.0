package com.smartseason.task.web;

import com.smartseason.task.platform.PageResponse;
import com.smartseason.task.service.TaskEvidenceService;
import com.smartseason.task.web.dto.TaskEvidenceCreateRequest;
import com.smartseason.task.web.dto.TaskEvidenceResponse;
import com.smartseason.task.web.dto.TaskEvidenceUpdateRequest;
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
@RequestMapping("/api/task/v1/task-evidence")
@Tag(name = "TaskEvidence", description = "Work orders, assignments, checklists, photo/GPS evidence, verification")
public class TaskEvidenceController {

    private final TaskEvidenceService service;

    public TaskEvidenceController(TaskEvidenceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List task-evidence for the caller's tenant")
    public PageResponse<TaskEvidenceResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single TaskEvidence by id")
    public TaskEvidenceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a TaskEvidence")
    public ResponseEntity<TaskEvidenceResponse> create(@Valid @RequestBody TaskEvidenceCreateRequest request) {
        TaskEvidenceResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/task/v1/task-evidence/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a TaskEvidence")
    public TaskEvidenceResponse update(@PathVariable UUID id, @Valid @RequestBody TaskEvidenceUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a TaskEvidence")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
