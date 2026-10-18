package com.swd392.aiviva.devicecheck.controller;

import com.swd392.aiviva.auth.security.JwtAuthenticationDetails;
import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.devicecheck.dto.response.DeviceCheckConfigResponse;
import com.swd392.aiviva.devicecheck.dto.response.DeviceCheckSttResponse;
import com.swd392.aiviva.devicecheck.service.DeviceCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Device Check", description = "Kiểm tra micro, loa và pipeline STT/TTS trước kỳ thi")
@RestController
@RequestMapping("/api/student/device-check")
public class DeviceCheckController {

    private final DeviceCheckService deviceCheckService;

    public DeviceCheckController(DeviceCheckService deviceCheckService) {
        this.deviceCheckService = deviceCheckService;
    }

    @Operation(summary = "Lấy cấu hình kiểm tra thiết bị")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Device-check configuration")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Student role required")
    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeviceCheckConfigResponse>> getConfig() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body(ApiResponse.success(
                        "Device-check configuration retrieved", deviceCheckService.getConfig()));
    }

    @Operation(summary = "Phát câu mẫu kiểm tra loa")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "MP3 audio sample",
            content = @Content(mediaType = "audio/mpeg", schema = @Schema(type = "string", format = "binary")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported language")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Student role required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "TTS provider failed")
    @GetMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> getTtsSample(
            @Parameter(description = "Language: vi or en")
            @RequestParam(defaultValue = "vi") String language) {
        byte[] audio = deviceCheckService.synthesizeTest(currentUserId(), language);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"device-check.mp3\"")
                .body(audio);
    }

    @Operation(summary = "Ghi âm câu mẫu và kiểm tra nhận diện giọng nói")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transcription result")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Empty audio, invalid language, or duration limit")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Student role required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Audio file too large")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "Unsupported or invalid audio type")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "STT provider failed")
    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeviceCheckSttResponse>> transcribe(
            @Parameter(description = "Audio file (webm, ogg, wav, mp3, mp4/m4a)")
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Parameter(description = "Language: vi or en")
            @RequestPart(value = "language", required = false) String language) {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body(ApiResponse.success(
                        "Device-check transcription completed",
                        deviceCheckService.transcribe(currentUserId(), file, language)));
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof JwtAuthenticationDetails details)
                || details.getUserId() == null) {
            throw new AccessDeniedException("A valid student JWT is required.");
        }
        return details.getUserId();
    }
}
