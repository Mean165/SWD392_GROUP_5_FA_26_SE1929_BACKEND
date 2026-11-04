package com.swd392.aiviva.speech.provider;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v2.ExplicitDecodingConfig;
import com.google.cloud.speech.v2.RecognitionConfig;
import com.google.cloud.speech.v2.RecognitionFeatures;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechSettings;
import com.google.cloud.speech.v2.StreamingRecognitionConfig;
import com.google.cloud.speech.v2.StreamingRecognitionFeatures;
import com.google.cloud.speech.v2.StreamingRecognizeRequest;
import com.google.cloud.speech.v2.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProvider;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderException;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextResultListener;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextStreamingSession;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "speech.stt", name = "provider", havingValue = "google", matchIfMissing = true)
public class GoogleCloudSpeechToTextProvider implements SpeechToTextProvider {

    private final SpeechToTextProperties properties;
    private volatile SpeechClient speechClient;

    public GoogleCloudSpeechToTextProvider(SpeechToTextProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        if (!"google".equalsIgnoreCase(properties.getProvider())
                || blank(properties.getGoogle().getProjectId())
                || blank(properties.getGoogle().getLocation())) {
            return false;
        }
        try {
            speechClient();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public String providerName() {
        return "google-cloud-speech-v2";
    }

    @Override
    public SpeechToTextStreamingSession openStreamingSession(
            String language, int sampleRate, SpeechToTextResultListener listener) {
        String locale = normalizeLanguage(language);
        if (sampleRate != 16000) {
            throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "Only 16000 Hz PCM audio is supported.");
        }

        SpeechClient client = speechClient();
        String recognizer = "projects/" + properties.getGoogle().getProjectId()
                + "/locations/" + properties.getGoogle().getLocation() + "/recognizers/_";
        RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                .setExplicitDecodingConfig(ExplicitDecodingConfig.newBuilder()
                        .setEncoding(ExplicitDecodingConfig.AudioEncoding.LINEAR16)
                        .setSampleRateHertz(sampleRate)
                        .setAudioChannelCount(1)
                        .build())
                .addLanguageCodes(locale)
                .setModel(properties.getGoogle().getModel())
                .setFeatures(RecognitionFeatures.newBuilder().setEnableAutomaticPunctuation(true).build())
                .build();
        StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                .setConfig(recognitionConfig)
                .setStreamingFeatures(StreamingRecognitionFeatures.newBuilder().setInterimResults(true).build())
                .build();

        ProviderSession session = new ProviderSession(listener);
        try {
            session.stream = client.streamingRecognizeCallable().splitCall(session);
            session.stream.send(StreamingRecognizeRequest.newBuilder()
                    .setRecognizer(recognizer)
                    .setStreamingConfig(streamingConfig)
                    .build());
            return session;
        } catch (RuntimeException exception) {
            session.abort();
            throw new SpeechToTextProviderException(
                    "PROVIDER_UNAVAILABLE", "Speech recognition provider could not open a stream.", exception);
        }
    }

    private SpeechClient speechClient() {
        SpeechClient current = speechClient;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (speechClient != null) {
                return speechClient;
            }
            if (blank(properties.getGoogle().getProjectId())) {
                throw new SpeechToTextProviderException(
                        "PROVIDER_UNAVAILABLE", "Google Cloud Speech is not configured.");
            }
            String endpoint = properties.getGoogle().getLocation() + "-speech.googleapis.com:443";
            try {
                speechClient = SpeechClient.create(SpeechSettings.newBuilder().setEndpoint(endpoint).build());
                return speechClient;
            } catch (IOException | RuntimeException exception) {
                throw new SpeechToTextProviderException(
                        "PROVIDER_UNAVAILABLE", "Google Cloud Speech credentials or connection are unavailable.", exception);
            }
        }
    }

    private String normalizeLanguage(String language) {
        if ("vi-VN".equalsIgnoreCase(language)) {
            return "vi-VN";
        }
        if ("en-US".equalsIgnoreCase(language)) {
            return "en-US";
        }
        throw new SpeechToTextProviderException("UNSUPPORTED_LANGUAGE", "Language must be vi-VN or en-US.");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        SpeechClient current = speechClient;
        if (current != null) {
            current.close();
        }
    }

    private static final class ProviderSession implements ResponseObserver<StreamingRecognizeResponse>, SpeechToTextStreamingSession {

        private final SpeechToTextResultListener listener;
        private final CompletableFuture<Void> completed = new CompletableFuture<>();
        private final StringBuilder finalTranscript = new StringBuilder();
        private volatile StreamController controller;
        private ClientStream<StreamingRecognizeRequest> stream;
        private volatile boolean inputClosed;
        private volatile boolean aborted;

        private ProviderSession(SpeechToTextResultListener listener) {
            this.listener = listener;
        }

        @Override
        public void onStart(StreamController controller) {
            this.controller = controller;
        }

        @Override
        public void onResponse(StreamingRecognizeResponse response) {
            for (var result : response.getResultsList()) {
                if (result.getAlternativesCount() == 0) {
                    continue;
                }
                var alternative = result.getAlternatives(0);
                String text = alternative.getTranscript().trim();
                if (text.isEmpty() || aborted) {
                    continue;
                }
                if (result.getIsFinal()) {
                    synchronized (finalTranscript) {
                        if (!finalTranscript.isEmpty()) {
                            finalTranscript.append(' ');
                        }
                        finalTranscript.append(text);
                    }
                    listener.onFinal(text, alternative.getConfidence());
                } else {
                    listener.onPartial(text);
                }
            }
        }

        @Override
        public void onError(Throwable error) {
            completed.completeExceptionally(error);
            if (!aborted) {
                listener.onError(error);
            }
        }

        @Override
        public void onComplete() {
            completed.complete(null);
        }

        @Override
        public synchronized void sendAudio(byte[] audioFrame) {
            if (inputClosed || aborted || stream == null) {
                throw new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "Speech recognition stream is closed.");
            }
            if (audioFrame == null || audioFrame.length == 0 || (audioFrame.length & 1) != 0) {
                throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "PCM16 audio frames must contain complete samples.");
            }
            try {
                stream.send(StreamingRecognizeRequest.newBuilder()
                        .setAudio(ByteString.copyFrom(audioFrame))
                        .build());
            } catch (RuntimeException exception) {
                throw new SpeechToTextProviderException(
                        "PROVIDER_UNAVAILABLE", "Speech recognition provider stopped accepting audio.", exception);
            }
        }

        @Override
        public String finish(Duration timeout) {
            synchronized (this) {
                if (!inputClosed && !aborted) {
                    inputClosed = true;
                    try {
                        stream.closeSend();
                    } catch (RuntimeException exception) {
                        abort();
                        throw new SpeechToTextProviderException(
                                "PROVIDER_UNAVAILABLE", "Speech recognition stream could not be finalized.", exception);
                    }
                }
            }
            try {
                completed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                synchronized (finalTranscript) {
                    return finalTranscript.toString();
                }
            } catch (TimeoutException exception) {
                abort();
                throw new SpeechToTextProviderException("PROVIDER_TIMEOUT", "Speech recognition provider timed out.", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                abort();
                throw new SpeechToTextProviderException("PROVIDER_TIMEOUT", "Speech recognition was interrupted.", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                String errorCode = cause != null && cause.getClass().getSimpleName().contains("DeadlineExceeded")
                        ? "PROVIDER_TIMEOUT" : "PROVIDER_UNAVAILABLE";
                throw new SpeechToTextProviderException(
                        errorCode, "Speech recognition provider failed.", cause);
            }
        }

        @Override
        public synchronized void abort() {
            if (aborted) {
                return;
            }
            aborted = true;
            inputClosed = true;
            StreamController currentController = controller;
            if (currentController != null) {
                try {
                    currentController.cancel();
                } catch (RuntimeException ignored) {
                    completed.complete(null);
                }
            } else if (stream != null) {
                try {
                    stream.closeSend();
                } catch (RuntimeException ignored) {
                    completed.complete(null);
                }
            }
            completed.complete(null);
        }
    }
}
