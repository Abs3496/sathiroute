package com.routemesh.shared.security;

import com.routemesh.shared.config.RouteMeshProperties;
import java.util.Set;
import java.util.Collection;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Turns the bearer token into a {@link Caller}. Dispatcher subjects come from configuration, not from token claims. */
@Component
public class CallerResolver implements HandlerMethodArgumentResolver {

    private final Set<String> dispatchers;
    private final Set<String> admins;
    private final Set<String> operationsManagers;
    private final Set<String> servicePartners;

    public CallerResolver(RouteMeshProperties props) {
        this.dispatchers = Set.copyOf(props.security().dispatcherSubjects());
        this.admins = Set.copyOf(props.security().adminSubjects());
        this.operationsManagers = Set.copyOf(props.security().operationsManagerSubjects());
        this.servicePartners = Set.copyOf(props.security().servicePartnerSubjects());
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Caller.class;
    }

    @Override
    public Caller resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                  WebDataBinderFactory binders) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt) || jwt.getSubject() == null) {
            throw new IllegalStateException("no authenticated subject on a /v1 request");
        }
        String subject = jwt.getSubject();
        Set<Role> assigned = new java.util.HashSet<>();
        if (admins.contains(subject)) assigned.add(Role.ADMIN);
        if (dispatchers.contains(subject)) assigned.add(Role.DISPATCHER);
        if (operationsManagers.contains(subject)) assigned.add(Role.OPERATIONS_MANAGER);
        if (servicePartners.contains(subject)) assigned.add(Role.SERVICE_PARTNER);
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        Set<String> roles = authorities.stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
        for (Role role : new Role[]{Role.ADMIN, Role.OPERATIONS_MANAGER, Role.SERVICE_PARTNER, Role.CUSTOMER, Role.SHIPPER, Role.DISPATCHER}) {
            if (roles.contains("ROLE_" + role.name())) assigned.add(role);
        }
        if (assigned.isEmpty()) assigned.add(Role.CUSTOMER);
        return new Caller(subject, assigned);
    }
}
