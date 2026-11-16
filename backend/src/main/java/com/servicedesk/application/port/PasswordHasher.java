package com.servicedesk.application.port;

/** Port: one-way password hashing (implemented with BCrypt in infrastructure). */
public interface PasswordHasher {

  String hash(String rawPassword);

  boolean matches(String rawPassword, String storedHash);
}
