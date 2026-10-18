package com.swd392.aiviva.devicecheck.service;

import com.swd392.aiviva.devicecheck.dto.response.DeviceCheckConfigResponse;
import com.swd392.aiviva.devicecheck.dto.response.DeviceCheckSttResponse;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface DeviceCheckService {

    DeviceCheckConfigResponse getConfig();

    byte[] synthesizeTest(UUID userId, String language);

    DeviceCheckSttResponse transcribe(UUID userId, MultipartFile file, String language);
}
