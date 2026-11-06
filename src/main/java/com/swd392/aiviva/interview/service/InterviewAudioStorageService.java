package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.common.config.InterviewConfig;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InterviewAudioStorageService {

    private static final Logger log = LoggerFactory.getLogger(InterviewAudioStorageService.class);

    private static final Pattern DATA_URL =
            Pattern.compile("^data:(audio/[a-z0-9.+-]+);base64,([A-Za-z0-9+/]*={0,2})$", Pattern.CASE_INSENSITIVE);

    private final Path storageDirectory;
    private final int maxAudioBytes;
    private final int retentionDays;

    public InterviewAudioStorageService(InterviewConfig config) {
        storageDirectory = Path.of(config.getAudioStorageDirectory()).toAbsolutePath().normalize();
        maxAudioBytes = config.getMaxAudioBytes();
        retentionDays = config.getAudioRetentionDays();
    }

    public StoredAudio store(UUID attemptId, String dataUrl) {
        Matcher matcher = parse(dataUrl);
        String contentType = matcher.group(1).toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Audio payload is not valid base64.");
        }
        if (bytes.length == 0 || bytes.length > maxAudioBytes) {
            throw new BusinessException("Audio payload is empty or exceeds the maximum size.");
        }

        String extension = extensionFor(contentType);
        String filename = UUID.randomUUID() + "." + extension;
        write(filename, bytes);
        return new StoredAudio("/api/interviews/" + attemptId + "/audio/" + filename, contentType);
    }

    public StoredAudio storeBytes(UUID attemptId, byte[] bytes, String contentType) {
        if (attemptId == null || bytes == null || bytes.length == 0 || bytes.length > maxAudioBytes) {
            throw new BusinessException("Audio payload is empty or exceeds the maximum size.");
        }
        if (!"audio/wav".equalsIgnoreCase(contentType)) {
            throw new BusinessException("Exam audio storage accepts PCM WAV only.");
        }
        String filename = UUID.randomUUID() + ".wav";
        write(filename, bytes);
        return new StoredAudio("/api/interviews/" + attemptId + "/audio/" + filename, "audio/wav");
    }

    @Scheduled(fixedDelay = 86_400_000L, initialDelay = 60_000L)
    public void deleteExpiredAudio() {
        try {
            if (!Files.isDirectory(storageDirectory)) return;
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            AtomicInteger deleted = new AtomicInteger();
            try (var files = Files.list(storageDirectory)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.(webm|wav|mp3|mp4|ogg|aac|flac)"))
                        .forEach(path -> {
                            try {
                                if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)
                                        && Files.deleteIfExists(path)) deleted.incrementAndGet();
                            } catch (IOException exception) {
                                log.warn("Exam audio retention cleanup failed for one file: exceptionType={}",
                                        exception.getClass().getSimpleName());
                            }
                        });
            }
            if (deleted.get() > 0) log.info("Exam audio retention cleanup deletedFiles={} retentionDays={}",
                    deleted.get(), retentionDays);
        } catch (IOException exception) {
            log.warn("Exam audio retention cleanup failed: exceptionType={}", exception.getClass().getSimpleName());
        }
    }

    public byte[] read(String filename) {
        if (filename == null || !filename.matches("[0-9a-fA-F-]{36}\\.(webm|wav|mp3|mp4|ogg|aac|flac)")) {
            throw new BusinessException("Invalid audio file name.");
        }
        Path path = storageDirectory.resolve(filename).normalize();
        if (!path.getParent().equals(storageDirectory)) {
            throw new BusinessException("Invalid audio file path.");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new ExternalServiceException("Stored interview audio could not be read.", exception);
        }
    }

    public MediaType mediaType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "webm" -> MediaType.parseMediaType("audio/webm");
            case "wav" -> MediaType.parseMediaType("audio/wav");
            case "mp3" -> MediaType.parseMediaType("audio/mpeg");
            case "mp4" -> MediaType.parseMediaType("audio/mp4");
            case "ogg" -> MediaType.parseMediaType("audio/ogg");
            case "aac" -> MediaType.parseMediaType("audio/aac");
            case "flac" -> MediaType.parseMediaType("audio/flac");
            default -> throw new BusinessException("Unsupported audio format.");
        };
    }

    private Matcher parse(String dataUrl) {
        if (dataUrl == null) {
            throw new BusinessException("Audio is required.");
        }
        Matcher matcher = DATA_URL.matcher(dataUrl);
        if (!matcher.matches()) {
            throw new BusinessException("Audio must be a supported base64 audio data URL.");
        }
        String contentType = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!switch (contentType) {
            case "audio/webm", "audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp4",
                    "audio/ogg", "audio/aac", "audio/flac" -> true;
            default -> false;
        }) {
            throw new BusinessException("Unsupported audio format.");
        }
        return matcher;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "audio/webm" -> "webm";
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4" -> "mp4";
            case "audio/ogg" -> "ogg";
            case "audio/aac" -> "aac";
            case "audio/flac" -> "flac";
            default -> throw new BusinessException("Unsupported audio format.");
        };
    }

    private void write(String filename, byte[] bytes) {
        try {
            Files.createDirectories(storageDirectory);
            Files.write(storageDirectory.resolve(filename), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new ExternalServiceException("Interview audio could not be stored.", exception);
        }
    }

    public record StoredAudio(String url, String contentType) {
    }
}
