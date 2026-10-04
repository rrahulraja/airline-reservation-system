package com.airline.booking.security;

public enum Role {

  ADMIN,
  CUSTOMER;

  /**
   * Spring Security expects authorities prefixed with ROLE_.
   */
  public String authority() {
    return "ROLE_" + name();
  }

}
