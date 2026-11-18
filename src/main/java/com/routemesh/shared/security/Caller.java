package com.routemesh.shared.security;

import com.routemesh.shared.error.ApiException;

/** A JWT caller. Passed explicitly into application services so authorisation is visible in the code. */
public record Caller(String subject, java.util.Set<Role> roles) {
    public Caller(String subject, Role role) {
        this(subject, java.util.Set.of(role));
    }

    public Caller {
        roles = roles == null || roles.isEmpty() ? java.util.Set.of(Role.CUSTOMER) : java.util.Set.copyOf(roles);
    }

    /** Compatibility accessor for logs and callers that expect a single primary role. */
    public Role role() {
        for (Role role : new Role[]{Role.ADMIN, Role.DISPATCHER, Role.OPERATIONS_MANAGER, Role.SERVICE_PARTNER,
                Role.CUSTOMER, Role.SHIPPER}) {
            if (roles.contains(role)) return role;
        }
        return Role.CUSTOMER;
    }

    public static Caller system(String job) {
        return new Caller("system:" + job, Role.DISPATCHER);
    }

    public boolean isDispatcher() {
        return roles.contains(Role.DISPATCHER) || roles.contains(Role.ADMIN);
    }

    public boolean isOperationsManager() {
        return roles.contains(Role.OPERATIONS_MANAGER);
    }

    public void requireDispatcher() {
        if (!isDispatcher()) {
            throw ApiException.forbidden("forbidden", "This needs the ADMIN role");
        }
    }

    public void requireOperations() {
        if (!isDispatcher() && !isOperationsManager()) {
            throw ApiException.forbidden("forbidden", "This needs an ADMIN or OPERATIONS_MANAGER role");
        }
    }

    public void requireAdmin() {
        if (!roles.contains(Role.ADMIN) && !roles.contains(Role.DISPATCHER)) {
            throw ApiException.forbidden("forbidden", "This needs the ADMIN role");
        }
    }

    public void requireCustomer() {
        if (!roles.contains(Role.CUSTOMER) && !roles.contains(Role.SHIPPER) && !roles.contains(Role.ADMIN)
                && !roles.contains(Role.DISPATCHER)) {
            throw ApiException.forbidden("forbidden", "This needs the CUSTOMER role");
        }
    }

    public void requireServicePartner() {
        if (!roles.contains(Role.SERVICE_PARTNER) && !roles.contains(Role.ADMIN) && !roles.contains(Role.DISPATCHER)) {
            throw ApiException.forbidden("forbidden", "This needs the SERVICE_PARTNER role");
        }
    }

    /** Shippers see their own shipments only. Dispatchers (and jobs) see everything. */
    public boolean canSee(String ownerSubject) {
        return isDispatcher() || subject.equals(ownerSubject);
    }
}
