package com.nexuswms.voice;

import org.springframework.stereotype.Service;

/** Text-box implementation of {@link VoiceService}. A SpeechToTextAdapter can feed it later. */
@Service
public class TextCommandVoiceService implements VoiceService {
    private final CommandParser parser; private final CommandExecutor executor;
    public TextCommandVoiceService(CommandParser parser, CommandExecutor executor) { this.parser = parser; this.executor = executor; }
    @Override public String handle(String text, Long actorId, boolean canMutate) { return executor.execute(parser.parse(text), actorId, canMutate); }
}
