package com.nexuswms.voice;

/** Pluggable voice/text command entry point. */
public interface VoiceService {
    /** Interpret and execute a command; returns a human-readable result. */
    String handle(String text, Long actorId, boolean canMutate);
}
