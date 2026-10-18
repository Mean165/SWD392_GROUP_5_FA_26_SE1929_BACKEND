package com.swd392.aiviva.devicecheck.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.common.exception.AudioTooLargeException;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import com.swd392.aiviva.common.exception.RateLimitExceededException;
import com.swd392.aiviva.common.exception.UnsupportedAudioException;
import com.swd392.aiviva.devicecheck.config.DeviceCheckProperties;
import com.swd392.aiviva.devicecheck.ratelimit.InMemoryRateLimiter;
import com.swd392.aiviva.interview.service.SpeechToTextService;
import com.swd392.aiviva.interview.service.TextToSpeechService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DeviceCheckServiceImplTest {

    private final UUID studentId = UUID.randomUUID();

    @Mock
    private SpeechToTextService speechToTextService;
    @Mock
    private TextToSpeechService textToSpeechService;

    private DeviceCheckProperties properties;
    private InMemoryRateLimiter rateLimiter;
    private DeviceCheckServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DeviceCheckProperties();
        rateLimiter = new InMemoryRateLimiter(properties);
        service = new DeviceCheckServiceImpl(properties, speechToTextService, textToSpeechService, rateLimiter);
    }

    @Test
    void configDoesNotCallProvidersOrUseRateLimit() {
        var config = service.getConfig();
        assertEquals(2 * 1024 * 1024, config.getMaxAudioBytes());
        assertEquals(15, config.getMaxDurationSeconds());
        assertEquals(5, config.getRecommendedRecordingSeconds());
        verifyNoInteractions(speechToTextService, textToSpeechService);
        for (int index = 0; index < 11; index++) {
            service.getConfig();
        }
        when(textToSpeechService.synthesize(properties.getTtsSample().get("vi"), "vi", "mp3"))
                .thenReturn(new byte[] {1});
        assertEquals(1, service.synthesizeTest(studentId, "vi").length);
    }

    @Test
    void ttsUsesFixedSampleAndCachesAudio() {
        byte[] audio = {1, 2, 3};
        when(textToSpeechService.synthesize(properties.getTtsSample().get("vi"), "vi", "mp3"))
                .thenReturn(audio);

        assertEquals(3, service.synthesizeTest(studentId, "vi").length);
        assertEquals(3, service.synthesizeTest(studentId, "vi").length);
        verify(textToSpeechService).synthesize(properties.getTtsSample().get("vi"), "vi", "mp3");
    }

    @Test
    void ttsRejectsUnsupportedLanguage() {
        assertThrows(BusinessException.class, () -> service.synthesizeTest(studentId, "fr"));
        verifyNoInteractions(textToSpeechService);
    }

    @Test
    void ttsProviderFailureIsSanitizedAsBadGateway() {
        when(textToSpeechService.synthesize(properties.getTtsSample().get("vi"), "vi", "mp3"))
                .thenThrow(new ExternalServiceException("internal provider detail"));

        ExternalServiceException exception = assertThrows(ExternalServiceException.class,
                () -> service.synthesizeTest(studentId, "vi"));
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("Device-check text-to-speech provider failed.", exception.getMessage());
    }

    @Test
    void sttRejectsMissingAndEmptyFiles() {
        assertThrows(BusinessException.class, () -> service.transcribe(studentId, null, "vi"));
        assertThrows(BusinessException.class, () -> service.transcribe(studentId,
                new MockMultipartFile("file", "empty.wav", "audio/wav", new byte[0]), "vi"));
        verifyNoInteractions(speechToTextService);
    }

    @Test
    void sttRejectsOversizedFileBeforeInspectingMime() {
        byte[] bytes = new byte[properties.getMaxAudioBytes() + 1];
        assertThrows(AudioTooLargeException.class, () -> service.transcribe(studentId,
                new MockMultipartFile("file", "too-big.bin", "application/octet-stream", bytes), "vi"));
        verifyNoInteractions(speechToTextService);
    }

    @Test
    void sttRejectsUnsupportedMimeAndMismatchedMagicBytes() {
        assertThrows(UnsupportedAudioException.class, () -> service.transcribe(studentId,
                new MockMultipartFile("file", "sample.wav", "application/octet-stream", wavHeader()), "vi"));
        assertThrows(UnsupportedAudioException.class, () -> service.transcribe(studentId,
                new MockMultipartFile("file", "sample.wav", "audio/wav", new byte[44]), "vi"));
        verifyNoInteractions(speechToTextService);
    }

    @Test
    void sttReturnsEmptyLevelForEmptyTranscript() {
        when(speechToTextService.transcribe(org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("device-check.wav"), org.mockito.ArgumentMatchers.eq("vi")))
                .thenReturn("  ");

        var response = service.transcribe(studentId,
                new MockMultipartFile("file", "sample.wav", "audio/wav", wavHeader()), "vi");
        assertEquals("", response.getTranscript());
        assertEquals("EMPTY", response.getLevel());
        assertEquals(0.0, response.getMatchScore());
        assertEquals("vi", response.getLanguage());
    }

    @Test
    void sttReturnsGoodMatchAndDuration() {
        when(speechToTextService.transcribe(org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("device-check.wav"), org.mockito.ArgumentMatchers.eq("vi")))
                .thenReturn(properties.getSample().get("vi"));

        var response = service.transcribe(studentId,
                new MockMultipartFile("file", "sample.wav", "audio/wav", wavHeader()), null);
        assertEquals("GOOD", response.getLevel());
        assertEquals(1.0, response.getMatchScore(), 0.0001);
        assertNotNull(response.getSuggestion());
        assertEquals(1, response.getDurationMs());
    }

    @Test
    void sttProviderFailureIsSanitizedAsBadGateway() {
        when(speechToTextService.transcribe(org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("device-check.wav"), org.mockito.ArgumentMatchers.eq("vi")))
                .thenThrow(new ExternalServiceException("internal provider detail"));

        ExternalServiceException exception = assertThrows(ExternalServiceException.class,
                () -> service.transcribe(studentId,
                        new MockMultipartFile("file", "sample.wav", "audio/wav", wavHeader()), "vi"));
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("Device-check speech-to-text provider failed.", exception.getMessage());
    }

    @Test
    void sttRejectsUnsupportedLanguageAfterFileValidation() {
        assertThrows(BusinessException.class, () -> service.transcribe(studentId,
                new MockMultipartFile("file", "sample.wav", "audio/wav", wavHeader()), "fr"));
        verify(speechToTextService, never()).transcribe(org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void sttRejectsWavLongerThanConfiguredDuration() {
        byte[] audio = new byte[44 + 512_000];
        byte[] header = wavHeader();
        System.arraycopy(header, 0, audio, 0, header.length);
        putLittleEndianInt(audio, 4, audio.length - 8);
        putLittleEndianInt(audio, 40, audio.length - 44);

        assertThrows(BusinessException.class, () -> service.transcribe(studentId,
                new MockMultipartFile("file", "long.wav", "audio/wav", audio), "vi"));
        verify(speechToTextService, never()).transcribe(org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void requestLimitIsSharedPerUserAcrossTtsAndSttButNotAcrossUsers() {
        when(textToSpeechService.synthesize(properties.getTtsSample().get("vi"), "vi", "mp3"))
                .thenReturn(new byte[] {1});
        UUID otherStudentId = UUID.randomUUID();
        for (int index = 0; index < 10; index++) {
            service.synthesizeTest(studentId, "vi");
        }
        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class,
                () -> service.transcribe(studentId, null, "vi"));
        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getRetryAfterSeconds() >= 1 && exception.getRetryAfterSeconds() <= 60);
        assertEquals(1, service.synthesizeTest(otherStudentId, "vi").length);
    }

    private byte[] wavHeader() {
        byte[] bytes = new byte[44];
        putAscii(bytes, 0, "RIFF");
        putLittleEndianInt(bytes, 4, 36);
        putAscii(bytes, 8, "WAVE");
        putAscii(bytes, 12, "fmt ");
        putLittleEndianInt(bytes, 16, 16);
        putLittleEndianInt(bytes, 20, 1);
        putLittleEndianInt(bytes, 22, 1);
        putLittleEndianInt(bytes, 24, 16_000);
        putLittleEndianInt(bytes, 28, 32_000);
        putLittleEndianInt(bytes, 32, 2);
        putLittleEndianInt(bytes, 34, 16);
        putAscii(bytes, 36, "data");
        putLittleEndianInt(bytes, 40, 0);
        return bytes;
    }

    private void putAscii(byte[] bytes, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            bytes[offset + index] = (byte) value.charAt(index);
        }
    }

    private void putLittleEndianInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }
}
