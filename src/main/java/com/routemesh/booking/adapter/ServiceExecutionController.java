package com.routemesh.booking.adapter;

import com.routemesh.booking.application.ServiceExecutionService;
import com.routemesh.booking.application.ServiceExecutionStore;
import com.routemesh.shared.security.Caller;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
class ServiceExecutionController {
    private final ServiceExecutionService execution;
    ServiceExecutionController(ServiceExecutionService execution) { this.execution = execution; }

    @PostMapping("/v1/partners/me/bookings/{id}/arrive")
    ServiceExecutionService.ExecutionView arrive(Caller caller, @PathVariable UUID id,
            @Valid @RequestBody ServiceExecutionService.PartnerAction in) {
        return execution.arrive(caller, id, in);
    }

    @PostMapping("/v1/bookings/{id}/completion-confirmation")
    ServiceExecutionService.ConfirmationView confirm(Caller caller, @PathVariable UUID id,
            @RequestBody ServiceExecutionService.CompletionConfirmation in) {
        return execution.confirmCompletion(caller, id, in);
    }

    @GetMapping("/v1/bookings/{id}/service-execution")
    ServiceExecutionService.ExecutionView get(Caller caller, @PathVariable UUID id) {
        return execution.get(caller, id);
    }

    @PostMapping("/v1/partners/me/bookings/{id}/evidence")
    ServiceExecutionService.EvidenceView evidence(Caller caller, @PathVariable UUID id,
            @Valid @RequestBody ServiceExecutionService.EvidenceIn in) {
        return execution.addEvidence(caller, id, in);
    }

    @GetMapping("/v1/bookings/{id}/evidence")
    List<ServiceExecutionService.EvidenceView> evidence(Caller caller, @PathVariable UUID id) {
        return execution.evidence(caller, id);
    }

    @GetMapping("/v1/bookings/{id}/evidence/{evidenceId}")
    ResponseEntity<byte[]> download(Caller caller, @PathVariable UUID id, @PathVariable UUID evidenceId) {
        var content = execution.evidence(caller, id, evidenceId);
        var disposition = ContentDisposition.attachment().filename(content.evidence().fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(content.evidence().contentType())).body(content.content());
    }

    @GetMapping("/v1/admin/service-executions")
    List<ServiceExecutionStore.Monitor> monitor(Caller caller, @RequestParam(required = false) String status) {
        return execution.monitor(caller, status);
    }
}
