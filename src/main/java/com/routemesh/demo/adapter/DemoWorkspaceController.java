package com.routemesh.demo.adapter;

import com.routemesh.demo.application.DemoWorkspace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Local-only showcase API. The whole controller is absent unless demo-api is explicitly enabled. */
@RestController
@ConditionalOnProperty(name = "routemesh.demo-api.enabled", havingValue = "true")
class DemoWorkspaceController {
    private final DemoWorkspace workspace;

    DemoWorkspaceController(DemoWorkspace workspace) { this.workspace = workspace; }

    @GetMapping("/dev/demo/workspace")
    Map<String, Object> workspace() { return workspace.snapshot(); }

    @PostMapping("/dev/demo/reset")
    Map<String, Object> reset() { workspace.reset(); return workspace.snapshot(); }

    @GetMapping("/dev/demo/reports")
    Object reports() { return workspace.report(); }

    @PostMapping("/dev/demo/bookings")
    Map<String, Object> createBooking(@RequestBody Map<String, Object> body) { return workspace.createBooking(body); }

    @PatchMapping("/dev/demo/bookings/{id}")
    Map<String, Object> updateBooking(@PathVariable String id, @RequestBody StatusIn body) { return workspace.updateBooking(id, body.status()); }

    @PostMapping("/dev/demo/partners")
    Map<String, Object> createPartner(@RequestBody Map<String, Object> body) { return workspace.createPartner(body); }

    @PatchMapping("/dev/demo/partners/{id}")
    Map<String, Object> updatePartner(@PathVariable String id, @RequestBody Map<String, Object> body) { return workspace.updatePartner(id, body); }

    @PostMapping("/dev/demo/shipments")
    Map<String, Object> createShipment(@RequestBody Map<String, Object> body) { return workspace.createShipment(body); }

    @PatchMapping("/dev/demo/shipments/{id}")
    Map<String, Object> updateShipment(@PathVariable String id, @RequestBody StatusIn body) { return workspace.updateShipment(id, body.status()); }

    @PostMapping("/dev/demo/services")
    Map<String, Object> createService(@RequestBody Map<String, Object> body) { return workspace.createService(body); }
    @PatchMapping("/dev/demo/services/{id}")
    Map<String, Object> updateService(@PathVariable String id, @RequestBody Map<String, Object> body) { return workspace.updateService(id, body); }

    record StatusIn(String status) { }
}
