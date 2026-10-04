package com.swd392.aiviva.user.mapper;

import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(), user.getRole(), user.getEnabled());
    }
}

