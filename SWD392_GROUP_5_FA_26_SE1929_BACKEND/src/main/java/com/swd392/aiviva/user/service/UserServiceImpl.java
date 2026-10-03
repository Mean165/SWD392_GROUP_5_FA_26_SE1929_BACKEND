package com.swd392.aiviva.user.service;

import com.swd392.aiviva.user.dto.request.CreateUserRequest;
import com.swd392.aiviva.user.dto.request.UpdateUserRequest;
import com.swd392.aiviva.user.dto.response.UserResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Override
    public List<UserResponse> getAllUsers() {
        // TODO: Implement list logic.
        return List.of();
    }

    @Override
    public UserResponse getUserById(Long id) {
        // TODO: Implement fetch logic.
        return null;
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        // TODO: Implement create logic.
        return null;
    }

    @Override
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        // TODO: Implement update logic.
        return null;
    }

    @Override
    public void deleteUser(Long id) {
        // TODO: Implement delete logic.
    }
}

