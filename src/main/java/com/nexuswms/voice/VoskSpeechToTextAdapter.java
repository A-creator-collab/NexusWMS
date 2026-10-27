package com.nexuswms.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import javax.sound.sampled.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Offline speech recognition with Vosk (Kaldi). 16 kHz / 16-bit / mono capture through javax.sound, endpointing by Vosk itself.
 * Install a model once (see README): download e.g. vosk-model-small-en-us-0.15, unzip, and set NEXUS_VOSK_MODEL_PATH.
 */
@Component
public class VoskSpeechToTextAdapter implements SpeechToTextAdapter {
    private static final Logger log = LoggerFactory.getLogger(VoskSpeechToTextAdapter.class);
    private static final float SAMPLE_RATE = 16000f;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private final ObjectMapper json = new ObjectMapper();
    private final String modelPath;
    private final int maxSeconds;
    private final AtomicBoolean listening = new AtomicBoolean();
    private volatile boolean cancelled;
    private volatile Model model;
    private volatile String loadError;

    public VoskSpeechToTextAdapter(@Value("${nexus.voice.model-path:}") String modelPath, @Value("${nexus.voice.max-seconds:8}") int maxSeconds) {
        this.modelPath = modelPath == null ? "" : modelPath.trim();
        this.maxSeconds = Math.max(2, Math.min(maxSeconds, 30));
    }

    @Override
    public boolean available() { return problem() == null; }

    @Override
    public String status() {
        String p = problem();
        return p == null ? "Voice ready (Vosk offline model: " + Path.of(modelPath).getFileName() + ")" : p;
    }

    private String problem() {
        if (modelPath.isEmpty()) return "Voice is not set up: set NEXUS_VOSK_MODEL_PATH to an unpacked Vosk model folder (see README).";
        if (!Files.isDirectory(Path.of(modelPath))) return "Vosk model folder not found: " + modelPath;
        if (loadError != null) return loadError;
        if (!AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, FORMAT))) return "No microphone available (16 kHz mono capture is not supported on this system).";
        return null;
    }

    @Override
    public Optional<String> listen(Consumer<String> partial) throws SpeechException {
        String p = problem();
        if (p != null) throw new SpeechException(p);
        if (!listening.compareAndSet(false, true)) throw new SpeechException("Already listening.");
        cancelled = false;
        TargetDataLine line = null;
        try (Recognizer rec = new Recognizer(model(), SAMPLE_RATE)) {
            line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, FORMAT));
            line.open(FORMAT);
            line.start();
            byte[] buf = new byte[4096];
            long deadline = System.nanoTime() + maxSeconds * 1_000_000_000L;
            String lastPartial = "";
            while (!cancelled && System.nanoTime() < deadline) {
                int n = line.read(buf, 0, buf.length);
                if (n <= 0) continue;
                if (rec.acceptWaveForm(buf, n)) {                       // Vosk detected the end of an utterance
                    String text = field(rec.getResult(), "text");
                    if (!text.isBlank()) return Optional.of(text);
                } else {
                    String part = field(rec.getPartialResult(), "partial");
                    if (!part.isBlank() && !part.equals(lastPartial)) { lastPartial = part; partial.accept(part); }
                }
            }
            String text = field(rec.getFinalResult(), "text");           // timeout or cancel: use what we have
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (LineUnavailableException e) {
            throw new SpeechException("The microphone is busy or blocked. Close other apps using it and check OS microphone permissions.", e);
        } catch (SecurityException e) {
            throw new SpeechException("Microphone access was denied by the operating system.", e);
        } catch (SpeechException e) {
            throw e;
        } catch (Exception e) {                                      // includes IOException from the native recogniser
            log.error("Speech recognition failed", e);
            throw new SpeechException("Speech recogniser error: " + e.getMessage(), e);
        } finally {
            if (line != null) { line.stop(); line.close(); }
            listening.set(false);
        }
    }

    @Override public void cancel() { cancelled = true; }

    private Model model() {
        Model m = model;
        if (m == null) {
            synchronized (this) {
                if (model == null) {
                    try {
                        LibVosk.setLogLevel(LogLevel.WARNINGS);
                        model = new Model(modelPath);
                    } catch (Throwable t) {                              // UnsatisfiedLinkError, bad model folder, ...
                        log.error("Could not load Vosk model from {}", modelPath, t);
                        loadError = "Could not load the Vosk model/native library: " + t.getMessage();
                        throw new SpeechException(loadError, t);
                    }
                }
                m = model;
            }
        }
        return m;
    }

    private String field(String vosk, String name) {
        try {
            JsonNode n = json.readTree(vosk);
            return n.path(name).asText("").trim();
        } catch (Exception e) { return ""; }
    }

    @PreDestroy void close() { try { if (model != null) model.close(); } catch (Exception ignored) { /* shutting down */ } }
}
