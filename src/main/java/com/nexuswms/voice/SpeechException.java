package com.nexuswms.voice;

/** Speech capture/recognition failure with a message that is safe to show to the user. */
public class SpeechException extends RuntimeException {
    public SpeechException(String message) { super(message); }
    public SpeechException(String message, Throwable cause) { super(message, cause); }
}
