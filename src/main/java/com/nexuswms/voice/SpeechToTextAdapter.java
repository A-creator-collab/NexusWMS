package com.nexuswms.voice;

import java.util.Optional;
import java.util.function.Consumer;

/** Speech-to-text boundary. The shipped implementation is {@link VoskSpeechToTextAdapter} (offline). */
public interface SpeechToTextAdapter {
    /** True when a recogniser model is installed and a microphone line is available. */
    boolean available();
    /** Human readable state / how to fix it. */
    String status();
    /**
     * Records from the default microphone until the speaker finishes one utterance (or the time limit passes) and returns the transcript.
     * Blocking: call from a background thread. {@code partial} receives live hypotheses (may be called from the recording thread).
     */
    Optional<String> listen(Consumer<String> partial) throws SpeechException;
    /** Stops an in-progress {@link #listen}; it then returns whatever was recognised so far. Safe to call from any thread. */
    void cancel();
}
