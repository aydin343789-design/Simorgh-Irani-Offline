package ir.simorgh.irani;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fully embedded English TTS. It never calls Android TTS or browser speechSynthesis.
 * The Piper Amy Medium neural voice is packaged in the APK and executed through Sherpa-ONNX.
 */
public final class SimorghSpeechBridge {
    private static final String ASSET_ZIP = "tts/tts-en.zip";
    private static final String MODEL_NAME = "en_US-amy-medium.onnx";
    private static final String ENGINE_NAME = "sherpa-onnx-piper-amy-medium-fp32";

    private final WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object engineLock = new Object();
    private volatile OfflineTts engine;
    private volatile AudioTrack activeTrack;
    private volatile boolean destroyed;
    private volatile String error = "";

    public SimorghSpeechBridge(WebView webView) {
        this.webView = webView;
        executor.execute(this::loadEngine);
    }

    @JavascriptInterface
    public boolean isReady() {
        return engine != null && !destroyed;
    }

    @JavascriptInterface
    public void refreshStatus() {
        emitStatus();
    }

    @JavascriptInterface
    public void speak(String text, String language, double rate, String callbackId) {
        if (destroyed || text == null || text.trim().isEmpty()) {
            emitCallback(callbackId, false, "empty_text");
            return;
        }
        if (language != null && !language.toLowerCase().startsWith("en")) {
            emitCallback(callbackId, false, "embedded_english_voice_only");
            return;
        }
        final String cleanText = text.trim();
        final float speed = Math.max(0.65f, Math.min((float) rate, 1.35f));
        executor.execute(() -> {
            try {
                loadEngine();
                OfflineTts current = engine;
                if (current == null) {
                    emitCallback(callbackId, false, "embedded_voice_not_ready:" + error);
                    return;
                }
                stopInternal();
                GeneratedAudio audio = current.generate(cleanText, 0, speed);
                play(audio);
                emitCallback(callbackId, true, "");
            } catch (Throwable failure) {
                error = safeMessage(failure);
                emitCallback(callbackId, false, "embedded_speech_failed:" + error);
            }
        });
    }

    @JavascriptInterface
    public void stop() {
        stopInternal();
    }

    public void destroy() {
        destroyed = true;
        stopInternal();
        executor.shutdownNow();
        synchronized (engineLock) {
            if (engine != null) {
                engine.release();
                engine = null;
            }
        }
        webView.removeJavascriptInterface("AndroidSpeech");
    }

    private void loadEngine() {
        if (destroyed || engine != null) return;
        synchronized (engineLock) {
            if (destroyed || engine != null) return;
            try {
                File root = new File(webView.getContext().getFilesDir(), "simorgh-tts/en");
                File model = new File(root, MODEL_NAME);
                if (!model.isFile() || !new File(root, "tokens.txt").isFile()
                        || !new File(root, "espeak-ng-data/phondata").isFile()) {
                    if (root.exists()) deleteRecursively(root);
                    if (!root.mkdirs() && !root.isDirectory()) throw new IOException("cannot_create_tts_directory");
                    extractAssetZip(root);
                }
                OfflineTtsVitsModelConfig vits = OfflineTtsVitsModelConfig.builder()
                        .setModel(model.getAbsolutePath())
                        .setTokens(new File(root, "tokens.txt").getAbsolutePath())
                        .setDataDir(new File(root, "espeak-ng-data").getAbsolutePath())
                        .build();
                OfflineTtsModelConfig config = OfflineTtsModelConfig.builder()
                        .setVits(vits).setNumThreads(2).setDebug(false).setProvider("cpu").build();
                engine = new OfflineTts(OfflineTtsConfig.builder()
                        .setModel(config).setMaxNumSentences(1).build());
                error = "";
                emitStatus();
            } catch (Throwable failure) {
                error = safeMessage(failure);
                emitStatus();
            }
        }
    }

    private void extractAssetZip(File destination) throws IOException {
        try (InputStream raw = webView.getContext().getAssets().open(ASSET_ZIP);
             java.util.zip.ZipInputStream input = new java.util.zip.ZipInputStream(new BufferedInputStream(raw))) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            String base = destination.getCanonicalPath() + File.separator;
            while ((entry = input.getNextEntry()) != null) {
                File output = new File(destination, entry.getName());
                if (!output.getCanonicalPath().startsWith(base)) throw new IOException("unsafe_tts_archive");
                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs()) throw new IOException("tts_mkdir_failed");
                } else {
                    File parent = output.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) throw new IOException("tts_parent_mkdir_failed");
                    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(output))) {
                        int count;
                        while ((count = input.read(buffer)) != -1) stream.write(buffer, 0, count);
                    }
                }
                input.closeEntry();
            }
        }
    }

    private void play(GeneratedAudio audio) throws IOException {
        float[] samples = audio.getSamples();
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            pcm[i] = (short) (Math.max(-1f, Math.min(1f, samples[i])) * 32767f);
        }
        int minBuffer = AudioTrack.getMinBufferSize(audio.getSampleRate(), AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) throw new IOException("audio_track_buffer_unavailable");
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(audio.getSampleRate())
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(Math.max(minBuffer, Math.min(pcm.length * 2, minBuffer * 8)))
                .setTransferMode(AudioTrack.MODE_STREAM).build();
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new IOException("audio_track_not_initialized");
        }
        activeTrack = track;
        try {
            track.play();
            int offset = 0;
            while (offset < pcm.length && !destroyed && track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                int count = track.write(pcm, offset, Math.min(4096, pcm.length - offset), AudioTrack.WRITE_BLOCKING);
                if (count < 0) throw new IOException("audio_track_write_failed");
                if (count == 0) {
                    SystemClock.sleep(4);
                    continue;
                }
                offset += count;
            }
            // AudioTrack.write() only queues PCM. Calling stop() immediately after
            // the last write cuts playback at the first buffer (often "ap" from
            // "apple"). Wait until the hardware has consumed every frame. There
            // is intentionally no fixed per-word timeout; long words/sentences
            // are allowed to finish naturally, while stop() still interrupts it.
            while (!destroyed
                    && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING
                    && track.getPlaybackHeadPosition() < pcm.length) {
                SystemClock.sleep(10);
            }
        } finally {
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
            activeTrack = null;
        }
    }

    private void stopInternal() {
        AudioTrack track = activeTrack;
        if (track != null) {
            try { track.stop(); } catch (Exception ignored) { }
        }
    }

    private void emitStatus() {
        boolean ready = isReady();
        runJavascript("if(typeof window.nativeSpeechStatus==='function'){window.nativeSpeechStatus(" + ready + ","
                + JSONObject.quote(error) + "," + JSONObject.quote(ENGINE_NAME) + ");}");
    }

    private void emitCallback(String callbackId, boolean success, String message) {
        if (callbackId == null || callbackId.isEmpty()) return;
        runJavascript("if(typeof window.__nativeSpeechCallback==='function'){window.__nativeSpeechCallback("
                + JSONObject.quote(callbackId) + "," + success + "," + JSONObject.quote(message) + ");}");
    }

    private void runJavascript(String script) {
        if (destroyed) return;
        webView.post(() -> { if (!destroyed) webView.evaluateJavascript(script, null); });
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
