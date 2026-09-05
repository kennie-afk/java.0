package com.smartseason.task.web;

import com.smartseason.task.platform.PageResponse;
import com.smartseason.task.service.TaskAssignmentService;
import com.smartseason.task.web.dto.TaskAssignmentCreateRequest;
import com.smartseason.task.web.dto.TaskAssignmentResponse;
import com.smartseason.task.web.dto.TaskAssignmentUpdateRequest;
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
@RequestMapping("/api/task/v1/task-assignments")
@Tag(name = "TaskAssignment", description = "Work orders, assignments, checklists, photo/GPS evidence, verification")
public class TaskAssignmentController {

    private final TaskAssignmentService service;

    public TaskAssignmentController(TaskAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List task-assignments for the caller's tenant")
    public PageResponse<TaskAssignmentResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single TaskAssignment by id")
    public TaskAssignmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a TaskAssignment")
    public ResponseEntity<TaskAssignmentResponse> create(@Valid @RequestBody TaskAssignmentCreateRequest request) {
        TaskAssignmentResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/task/v1/task-assignments/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a TaskAssignment")
    public TaskAssignmentResponse update(@PathVariable UUID id, @Valid @RequestBody TaskAssignmentUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a TaskAssignment")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
