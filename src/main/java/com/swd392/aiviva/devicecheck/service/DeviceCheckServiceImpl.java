package com.swd392.aiviva.devicecheck.service;

import com.swd392.aiviva.common.exception.AudioTooLargeException;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import com.swd392.aiviva.common.exception.RateLimitExceededException;
import com.swd392.aiviva.common.exception.UnsupportedAudioException;
import com.swd392.aiviva.devicecheck.config.DeviceCheckProperties;
import com.swd392.aiviva.devicecheck.dto.response.DeviceCheckConfigResponse;
import com.swd392.aiviva.devicecheck.dto.response.DeviceCheckSttResponse;
import com.swd392.aiviva.devicecheck.ratelimit.InMemoryRateLimiter;
import com.swd392.aiviva.devicecheck.util.TextMatchUtil;
import com.swd392.aiviva.interview.service.SpeechToTextService;
import com.swd392.aiviva.interview.service.TextToSpeechService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DeviceCheckServiceImpl implements DeviceCheckService {

    private static final Logger log = LoggerFactory.getLogger(DeviceCheckServiceImpl.class);

    private final DeviceCheckProperties properties;
    private final SpeechToTextService speechToTextService;
    private final TextToSpeechService textToSpeechService;
    private final InMemoryRateLimiter rateLimiter;
    private final Map<String, byte[]> ttsAudioCache = new ConcurrentHashMap<>();

    public DeviceCheckServiceImpl(
            DeviceCheckProperties properties,
            SpeechToTextService speechToTextService,
            TextToSpeechService textToSpeechService,
            InMemoryRateLimiter rateLimiter) {
        this.properties = properties;
        this.speechToTextService = speechToTextService;
        this.textToSpeechService = textToSpeechService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public DeviceCheckConfigResponse getConfig() {
        return DeviceCheckConfigResponse.builder()
                .languages(properties.getLanguages())
                .defaultLanguage(properties.getDefaultLanguage())
                .acceptedMimeTypes(properties.getAcceptedMimeTypes())
                .maxAudioBytes(properties.getMaxAudioBytes())
                .maxDurationSeconds(properties.getMaxDurationSeconds())
                .recommendedRecordingSeconds(properties.getRecommendedRecordingSeconds())
                .sampleSentences(properties.getSample())
                .build();
    }

    @Override
    public byte[] synthesizeTest(UUID userId, String requestedLanguage) {
        long started = System.nanoTime();
        String result = "ERROR";
        try {
            rateLimiter.check(userId);
            String language = normalizeLanguage(requestedLanguage);
            String sample = properties.getTtsSample().get(language);
            if (sample == null || sample.isBlank()) {
                throw new BusinessException("No device-check TTS sample is configured for this language.");
            }
            byte[] audio = cachedTts(language, sample);
            result = "OK";
            return audio.clone();
        } catch (RateLimitExceededException exception) {
            result = "429";
            throw exception;
        } catch (BusinessException exception) {
            result = "400";
            throw exception;
        } catch (ExternalServiceException exception) {
            result = "502";
            throw new ExternalServiceException("Device-check text-to-speech provider failed.", HttpStatus.BAD_GATEWAY);
        } finally {
            log.info("device-check userId={} endpoint=tts result={} durationMs={}",
                    userId, result, elapsedMillis(started));
        }
    }

    @Override
    public DeviceCheckSttResponse transcribe(UUID userId, MultipartFile file, String requestedLanguage) {
        long started = System.nanoTime();
        String result = "ERROR";
        try {
            rateLimiter.check(userId);
            if (file == null || file.isEmpty()) {
                throw new BusinessException("Audio file is required and must not be empty.");
            }
            if (file.getSize() > properties.getMaxAudioBytes()) {
                throw new AudioTooLargeException("Audio file exceeds the maximum size of "
                        + properties.getMaxAudioBytes() + " bytes.");
            }

            String contentType = normalizeMimeType(file.getContentType());
            if (!properties.getAcceptedMimeTypes().stream()
                    .map(DeviceCheckServiceImpl::normalizeMimeType)
                    .anyMatch(contentType::equals)) {
                throw new UnsupportedAudioException("Unsupported audio content type.");
            }

            byte[] audio;
            try {
                audio = file.getBytes();
            } catch (IOException exception) {
                throw new BusinessException("Audio file could not be read.");
            }
            if (audio.length == 0) {
                throw new BusinessException("Audio file is required and must not be empty.");
            }
            if (audio.length > properties.getMaxAudioBytes()) {
                throw new AudioTooLargeException("Audio file exceeds the maximum size of "
                        + properties.getMaxAudioBytes() + " bytes.");
            }
            if (!hasExpectedMagicBytes(audio, contentType)) {
                throw new UnsupportedAudioException("Audio file content does not match its content type.");
            }

            String language = normalizeLanguage(requestedLanguage);
            long durationMs = estimateDurationMs(audio, contentType);
            if (durationMs > properties.getMaxDurationSeconds() * 1000L) {
                throw new BusinessException("Audio duration exceeds the maximum of "
                        + properties.getMaxDurationSeconds() + " seconds.");
            }

            String transcript;
            try {
                transcript = speechToTextService.transcribe(audio, filenameFor(contentType), language);
            } catch (ExternalServiceException exception) {
                result = "502";
                log.warn("Device-check STT provider failure userId={} status={} exceptionType={}",
                        userId, exception.getStatus().value(), exception.getClass().getSimpleName());
                throw new ExternalServiceException("Device-check speech-to-text provider failed.",
                        HttpStatus.BAD_GATEWAY);
            }
            transcript = transcript == null ? "" : transcript.trim();
            double matchScore = TextMatchUtil.score(properties.getSample().get(language), transcript);
            String level = levelFor(transcript, matchScore);
            DeviceCheckSttResponse response = DeviceCheckSttResponse.builder()
                    .transcript(transcript)
                    .matchScore(matchScore)
                    .level(level)
                    .durationMs(durationMs)
                    .language(language)
                    .suggestion(suggestionFor(level))
                    .build();
            result = "OK";
            return response;
        } catch (RateLimitExceededException exception) {
            result = "429";
            throw exception;
        } catch (AudioTooLargeException exception) {
            result = "413";
            throw exception;
        } catch (UnsupportedAudioException exception) {
            result = "415";
            throw exception;
        } catch (BusinessException exception) {
            result = "400";
            throw exception;
        } finally {
            log.info("device-check userId={} endpoint=stt result={} durationMs={}",
                    userId, result, elapsedMillis(started));
        }
    }

    private byte[] cachedTts(String language, String sample) {
        byte[] audio = ttsAudioCache.get(language);
        if (audio != null) {
            return audio;
        }
        synchronized (ttsAudioCache) {
            audio = ttsAudioCache.get(language);
            if (audio == null) {
                if (ttsAudioCache.size() >= 2) {
                    String evict = ttsAudioCache.keySet().stream()
                            .filter(key -> !key.equals(language))
                            .findFirst()
                            .orElse(null);
                    if (evict != null) {
                        ttsAudioCache.remove(evict);
                    }
                }
                audio = textToSpeechService.synthesize(sample, language, "mp3");
                ttsAudioCache.put(language, audio.clone());
            }
        }
        return audio;
    }

    private String normalizeLanguage(String requestedLanguage) {
        String language = requestedLanguage == null || requestedLanguage.isBlank()
                ? properties.getDefaultLanguage()
                : requestedLanguage.trim().toLowerCase(Locale.ROOT);
        boolean supported = properties.getLanguages().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(language::equals);
        if (!supported) {
            throw new BusinessException("Language must be one of the configured device-check languages.");
        }
        return language;
    }

    private static String normalizeMimeType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameterStart = contentType.indexOf(';');
        return (parameterStart < 0 ? contentType : contentType.substring(0, parameterStart))
                .trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasExpectedMagicBytes(byte[] audio, String contentType) {
        return switch (contentType) {
            case "audio/wav", "audio/x-wav" -> startsWith(audio, "RIFF")
                    && audio.length >= 12 && asciiAt(audio, 8, "WAVE");
            case "audio/ogg" -> startsWith(audio, "OggS");
            case "audio/webm" -> startsWith(audio, new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3});
            case "audio/mpeg" -> startsWith(audio, "ID3")
                    || (audio.length >= 2 && (audio[0] & 0xff) == 0xff
                    && (audio[1] & 0xe0) == 0xe0 && (audio[1] & 0x06) == 0x02);
            case "audio/mp4", "audio/m4a" -> audio.length >= 8 && asciiAt(audio, 4, "ftyp");
            default -> false;
        };
    }

    private long estimateDurationMs(byte[] audio, String contentType) {
        if ("audio/wav".equals(contentType) || "audio/x-wav".equals(contentType)) {
            long wavDuration = wavDurationMs(audio);
            if (wavDuration > 0) {
                return wavDuration;
            }
        }
        long bitsPerSecond = switch (contentType) {
            case "audio/webm" -> 64_000;
            case "audio/ogg" -> 96_000;
            case "audio/wav", "audio/x-wav" -> 705_600;
            case "audio/mpeg" -> 128_000;
            case "audio/mp4", "audio/m4a" -> 96_000;
            default -> 96_000;
        };
        return Math.max(1, (long) audio.length * 8_000 / bitsPerSecond);
    }

    private long wavDurationMs(byte[] audio) {
        long byteRate = 0;
        long dataBytes = 0;
        int offset = 12;
        while (offset + 8 <= audio.length) {
            String chunk = new String(audio, offset, 4, StandardCharsets.US_ASCII);
            long chunkSize = unsignedIntLittleEndian(audio, offset + 4);
            int chunkData = offset + 8;
            if ("fmt ".equals(chunk) && chunkSize >= 12 && chunkData + 12 <= audio.length) {
                byteRate = unsignedIntLittleEndian(audio, chunkData + 8);
            } else if ("data".equals(chunk)) {
                dataBytes = Math.min(chunkSize, Math.max(0, audio.length - chunkData));
            }
            if (chunkSize > Integer.MAX_VALUE || chunkData + chunkSize > audio.length) {
                break;
            }
            offset = chunkData + (int) chunkSize + (int) (chunkSize & 1);
        }
        return byteRate > 0 && dataBytes > 0 ? dataBytes * 1000 / byteRate : 0;
    }

    private long unsignedIntLittleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL)
                | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16)
                | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private static boolean startsWith(byte[] value, String prefix) {
        return asciiAt(value, 0, prefix);
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiAt(byte[] value, int offset, String expected) {
        if (offset < 0 || value.length < offset + expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (value[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private String filenameFor(String contentType) {
        return "device-check." + switch (contentType) {
            case "audio/webm" -> "webm";
            case "audio/ogg" -> "ogg";
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4" -> "mp4";
            case "audio/m4a" -> "m4a";
            default -> throw new UnsupportedAudioException("Unsupported audio content type.");
        };
    }

    private String levelFor(String transcript, double score) {
        if (transcript.isBlank()) {
            return "EMPTY";
        }
        if (score >= 0.8) {
            return "GOOD";
        }
        if (score >= 0.5) {
            return "FAIR";
        }
        return "POOR";
    }

    private String suggestionFor(String level) {
        return switch (level) {
            case "EMPTY" -> "Không nghe thấy giọng nói, hãy kiểm tra micro và nói to hơn";
            case "POOR" -> "Nhận diện chưa tốt, hãy nói chậm, rõ và giảm tiếng ồn";
            case "FAIR" -> "Tạm ổn, nên nói rõ hơn một chút";
            case "GOOD" -> "Micro hoạt động tốt";
            default -> "Hãy thử ghi âm lại";
        };
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
