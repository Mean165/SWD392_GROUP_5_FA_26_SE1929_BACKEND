package com.swd392.aiviva.exam.dto.request;

import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class BulkAssignStudentsRequest {
    private List<UUID> studentIds;
    private List<String> studentCodes;
}
