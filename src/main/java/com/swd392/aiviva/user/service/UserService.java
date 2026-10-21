package com.swd392.aiviva.user.service;

import com.swd392.aiviva.user.dto.request.CreateUserRequest;
import com.swd392.aiviva.user.dto.request.UpdateUserRequest;
import com.swd392.aiviva.user.dto.request.UserFilterRequest;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.dto.response.LecturerSummaryResponse;
import com.swd392.aiviva.common.response.PageResponse;
import java.util.UUID;
import java.util.List;

public interface UserService {

    List<UserResponse> getAllUsers();

    UserResponse getUserById(String id);

    List<UserResponse> filterUsers(UserFilterRequest request);

    PageResponse<UserResponse> searchManageableUsers(UserFilterRequest request, int page, int size);

    List<LecturerSummaryResponse> listLecturers();

    LecturerSummaryResponse getLecturer(UUID lecturerId);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(String id, UpdateUserRequest request);

    UserResponse lockUser(UUID id);

    UserResponse unlockUser(UUID id);

    void resetPassword(UUID id, String newPassword);

    void deleteUser(UUID id);
}
