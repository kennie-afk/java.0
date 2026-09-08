package com.smartseason.task.mywork;

import com.smartseason.task.web.dto.TaskAssignmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task/v1/my-work")
@Tag(name = "My work", description = "A worker's own assignments, and recording time against them")
public class MyWorkController {

    private static final List<String> SUPERVISING =
            List.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_FARMER");

    private final MyWorkService service;

    public MyWorkController(MyWorkService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'WORKER')")
    @Operation(summary = "Assignments belonging to the signed-in account")
    public List<TaskAssignmentResponse> mine(Authentication authentication) {
        return service.mine(callerId(authentication)).stream()
                .map(TaskAssignmentResponse::from).toList();
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'WORKER')")
    @Operation(summary = "Record the start of work; the time comes from the server")
    public TaskAssignmentResponse start(@PathVariable UUID id, Authentication authentication) {
        return TaskAssignmentResponse.from(
                service.start(id, callerId(authentication), supervising(authentication)));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'WORKER')")
    @Operation(summary = "Record the end of work; the time comes from the server")
    public TaskAssignmentResponse stop(@PathVariable UUID id, Authentication authentication) {
        return TaskAssignmentResponse.from(
                service.stop(id, callerId(authentication), supervising(authentication)));
    }

    private static UUID callerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static boolean supervising(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SUPERVISING::contains);
    }
}
