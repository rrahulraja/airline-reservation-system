package com.airline.booking.security;

/**
 * The authenticated principal, carried in the SecurityContext.
 *
 * <p>Deliberately not the {@link AppUser} entity: putting a JPA entity in the
 * security context risks lazy-loading outside a transaction and keeps a managed
 * instance alive for the whole request. This record is detached data.
 */
public record AuthenticatedUser(Long userId, String username, Role role) {

}
