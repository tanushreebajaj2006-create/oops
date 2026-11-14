package com.servicedesk.api;

import com.servicedesk.api.dto.Requests;
import com.servicedesk.api.dto.Views;
import com.servicedesk.application.AuthService;
import com.servicedesk.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Login, self-registration (employees) and "who am I". */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService auth;
  private final UserService users;

  public AuthController(AuthService auth, UserService users) {
    this.auth = auth;
    this.users = users;
  }

  @PostMapping("/login")
  public Views.Session login(@Valid @RequestBody Requests.Login body) {
    return session(auth.login(body.email(), body.password()));
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Views.Session register(@Valid @RequestBody Requests.Register body) {
    return session(
        auth.register(
            new AuthService.RegisterCommand(
                body.fullName(), body.email(), body.password(), body.department())));
  }

  @GetMapping("/me")
  public Views.UserView me(Authentication authentication) {
    return ApiMapper.user(users.me(CurrentUser.id(authentication)));
  }

  private static Views.Session session(AuthService.Session s) {
    return new Views.Session(
        s.token().value(), "Bearer", s.token().expiresAt(), ApiMapper.user(s.user()));
  }
}
