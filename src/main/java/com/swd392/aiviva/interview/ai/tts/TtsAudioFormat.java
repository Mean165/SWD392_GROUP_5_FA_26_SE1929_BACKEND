package com.swd392.aiviva.interview.ai.tts;

public final class TtsAudioFormat {

    private TtsAudioFormat() {
    }

    public static String contentType(byte[] audio) {
        return isWave(audio) ? "audio/wav" : "audio/mpeg";
    }

    public static String extension(byte[] audio) {
        return isWave(audio) ? "wav" : "mp3";
    }

    private static boolean isWave(byte[] audio) {
        return audio != null && audio.length >= 12
                && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F'
                && audio[8] == 'W' && audio[9] == 'A' && audio[10] == 'V' && audio[11] == 'E';
    }
}
