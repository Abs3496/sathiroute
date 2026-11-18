package com.routemesh.shared.security;

/** A role asserted by the trusted identity provider or assigned from trusted operator configuration. */
public enum Role {
    CUSTOMER,
    SERVICE_PARTNER,
    OPERATIONS_MANAGER,
    ADMIN,
    /** Compatibility role for the existing RouteMesh dispatcher identity configuration. */
    DISPATCHER,
    /** Compatibility alias for prior shipment accounts. */
    SHIPPER,
}
