package com.swd392.aiviva.user.service;

import com.swd392.aiviva.user.dto.request.CreateUserRequest;
import com.swd392.aiviva.user.dto.request.UpdateUserRequest;
import com.swd392.aiviva.user.dto.request.UserFilterRequest;
import com.swd392.aiviva.user.dto.response.UserResponse;
import java.util.List;

public interface UserService {

    List<UserResponse> getAllUsers();

    UserResponse getUserByIdentifier(String identifier);

    List<UserResponse> filterUsers(UserFilterRequest filterRequest);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(String identifier, UpdateUserRequest request);

    void deleteUser(Long id);
}
