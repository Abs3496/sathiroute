package com.routemesh.routing.adapter;

import com.routemesh.routing.application.PartnerRouteService;
import com.routemesh.routing.application.PartnerRouteStore;
import com.routemesh.shared.security.Caller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
class PartnerRouteController {
    private final PartnerRouteService routes;

    PartnerRouteController(PartnerRouteService routes) { this.routes = routes; }

    @PostMapping("/v1/admin/partners/{partnerId}/routes/{date}/recalculate")
    PartnerRouteService.RouteView recalculate(Caller caller, @PathVariable UUID partnerId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return routes.recalculate(caller, partnerId, date);
    }

    @GetMapping("/v1/partners/{partnerId}/routes/{date}")
    PartnerRouteService.RouteView get(Caller caller, @PathVariable UUID partnerId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return routes.get(caller, partnerId, date);
    }

    @GetMapping("/v1/admin/partner-routes/{routeId}/history")
    List<PartnerRouteStore.History> history(Caller caller, @PathVariable UUID routeId) {
        return routes.history(caller, routeId);
    }
}
