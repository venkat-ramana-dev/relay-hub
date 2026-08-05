package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.request.CreateUserRequest;
import dev.venkat.relayhub.dto.response.CreateUserResponse;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        CreateUserResponse createUserResponse = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createUserResponse);
    }

}
