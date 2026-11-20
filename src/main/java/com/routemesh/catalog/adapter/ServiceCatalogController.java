package com.routemesh.catalog.adapter;

import com.routemesh.catalog.application.CatalogStore.Service;
import com.routemesh.catalog.application.ServiceCatalogService;
import com.routemesh.catalog.application.ServiceCatalogService.CreateIn;
import com.routemesh.catalog.application.ServiceCatalogService.UpdateIn;
import com.routemesh.shared.security.Caller;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
class ServiceCatalogController {
    private final ServiceCatalogService services;

    ServiceCatalogController(ServiceCatalogService services) { this.services = services; }

    @GetMapping("/v1/admin/services")
    List<Service> list(Caller caller) { return services.list(caller); }

    @PostMapping("/v1/admin/services")
    @ResponseStatus(HttpStatus.CREATED)
    Service create(Caller caller, @Valid @RequestBody CreateIn in) { return services.create(caller, in); }

    @PatchMapping("/v1/admin/services/{id}")
    Service update(Caller caller, @PathVariable UUID id, @Valid @RequestBody UpdateIn in) {
        return services.update(caller, id, in);
    }
}
