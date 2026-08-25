package com.novelrealm.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novelrealm.dto.auth.LoginRequest;
import com.novelrealm.dto.auth.LoginResponse;
import com.novelrealm.dto.auth.RegisterRequest;
import com.novelrealm.dto.user.UserResponse;
import com.novelrealm.model.User;
import com.novelrealm.service.auth.AuthenticationService;
import com.novelrealm.service.user.UserMapper;
import com.novelrealm.service.user.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final UserMapper userMapper;

    public AuthController(
            UserService userService,
            AuthenticationService authenticationService,
            UserMapper userMapper) {
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody @Valid RegisterRequest request) {
        User user = userService.register(
                request.pseudo(),
                request.email(),
                request.password(),
                User.AuthProvider.LOCAL); // ← inscription classique = compte LOCAL

        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toOwnResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse httpResponse) {

        // 1. Vérifier les identifiants
        User user = userService.login(request.email(), request.password());

        // 2. Émettre le JWT : cookie httpOnly (web) + token renvoyé dans le body (mobile)
        String token = authenticationService.authenticate(user, httpResponse);

        return ResponseEntity.ok(new LoginResponse(userMapper.toOwnResponse(user), token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authenticationService.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build(); // 204 : "c'est fait, rien à renvoyer"
    }
}