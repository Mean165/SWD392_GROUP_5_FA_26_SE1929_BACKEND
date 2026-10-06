package com.swd392.aiviva.user.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleResponse {

    private UUID roleId;
    private String roleCode;
    private String roleName;
    private String description;
    private OffsetDateTime createdAt;
}
