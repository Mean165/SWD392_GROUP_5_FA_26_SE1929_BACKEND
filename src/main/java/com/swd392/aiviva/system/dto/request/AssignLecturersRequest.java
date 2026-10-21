package com.swd392.aiviva.system.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class AssignLecturersRequest {
    @NotEmpty(message = "At least one Lecturer ID is required")
    private List<@NotNull(message = "Lecturer IDs cannot contain null") UUID> lecturerIds;
}
