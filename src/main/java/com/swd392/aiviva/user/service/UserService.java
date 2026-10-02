package com.swd392.aiviva.user.service;

import com.swd392.aiviva.user.dto.request.CreateUserRequest;
import com.swd392.aiviva.user.dto.request.UpdateUserRequest;
import com.swd392.aiviva.user.dto.request.UserFilterRequest;
import com.swd392.aiviva.user.dto.response.UserResponse;
import java.util.List;
import java.util.UUID;

public interface UserService {

    List<UserResponse> getAllUsers();

    UserResponse getUserById(String id);

    List<UserResponse> filterUsers(UserFilterRequest request);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(String id, UpdateUserRequest request);

    void deleteUser(UUID id);
}
