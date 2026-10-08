package ir.simorgh.irani;

import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@CapacitorPlugin(name = "SimorghSpeech")
public class SimorghSpeechPlugin extends Plugin {
    private static final String TAG = "SimorghSpeech";
    private static final String VOICE_NAME = "Amy (English US, medium, INT8)";
    private static final String ASSET_NAME = "tts/tts-en.zip";
    private static final String MODEL_RELATIVE_DIR = "simorgh-tts/en-amy-int8-v1";
    private static final String MODEL_NAME = "en_US-amy-medium.onnx";
    private static final String MODEL_SHA256 = "e93cf3361c5561b9fedcec20b26ae5ecd068172e514740562f1263be65b3848f";
    private static final long MODEL_SIZE_BYTES = 18_681_741L;
    private static final long MAX_EXTRACTED_BYTES = 25L * 1024L * 1024L;
    private static final int MAX_ARCHIVE_ENTRIES = 300;

    private final ExecutorService speechQueue = Executors.newSingleThreadExecutor();
    private final Object engineLock = new Object();
    private final AtomicLong requestGeneration = new AtomicLong(0);

    private volatile OfflineTts engine;
    private volatile AudioTrack activeTrack;
    private volatile boolean loading;
    private volatile int progress;
    private volatile String lastError = "";

    @Override
    public void load() {
        super.load();
        try {
            speechQueue.execute(() -> {
                try {
                    ensureEngine();
                } catch (Throwable error) {
                    lastError = safeMessage(error);
                    Log.e(TAG, "Could not initialize bundled English voice", error);
                }
            });
        } catch (Exception error) {
            lastError = safeMessage(error);
            Log.e(TAG, "Could not queue voice initialization", error);
        }
    }

    @Override
    protected void handleOnDestroy() {
        requestGeneration.incrementAndGet();
        stopActiveTrack();
        try {
            speechQueue.execute(() -> {
                synchronized (engineLock) {
                    if (engine != null) {
                        try { engine.release(); } catch (Exception ignored) { }
                        engine = null;
                    }
                }
            });
            speechQueue.shutdown();
        } catch (Exception error) {
            Log.w(TAG, "TTS shutdown", error);
        }
        super.handleOnDestroy();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject result = new JSObject();
        result.put("ready", engine != null);
        result.put("loading", loading);
        result.put("progress", progress);
        result.put("offline", true);
        result.put("embedded", true);
        result.put("language", "en-US");
        result.put("voice", VOICE_NAME);
        result.put("modelBytes", MODEL_SIZE_BYTES);
        result.put("error", lastError);
        result.put("engine", "Sherpa-ONNX Piper INT8");
        call.resolve(result);
    }

    @PluginMethod
    public void speak(PluginCall call) {
        final String text = call.getString("text", "");
        final String language = call.getString("lang", "en-US");
        final Double requestedRate = call.getDouble("rate", 0.85);
        final String cleanText = text == null ? "" : text.trim();

        if (cleanText.isEmpty()) {
            call.reject("empty_text");
            return;
        }
        if (cleanText.length() > 600) {
            call.reject("text_too_long_max_600_characters");
            return;
        }
        if (containsPersian(cleanText) || (language != null && language.toLowerCase(Locale.ROOT).startsWith("fa"))) {
            call.reject("this_bundled_voice_supports_english_only");
            return;
        }
        if (!cleanText.matches("(?s).*\\p{IsLatin}.*")) {
            call.reject("no_english_text_to_speak");
            return;
        }

        final float speed = requestedRate == null
            ? 0.85f
            : Math.max(0.70f, Math.min(requestedRate.floatValue(), 1.20f));
        final long requestId = requestGeneration.incrementAndGet();
        stopActiveTrack();

        try {
            speechQueue.execute(() -> {
                try {
                    if (requestId != requestGeneration.get()) {
                        resolveCancelled(call, language);
                        return;
                    }
                    ensureEngine();
                    if (requestId != requestGeneration.get()) {
                        resolveCancelled(call, language);
                        return;
                    }

                    OfflineTts localEngine = engine;
                    if (localEngine == null) {
                        call.reject("offline_english_voice_not_ready: " + lastError);
                        return;
                    }

                    GeneratedAudio audio = localEngine.generate(cleanText, 0, speed);
                    if (requestId != requestGeneration.get()) {
                        resolveCancelled(call, language);
                        return;
                    }
                    playAudio(audio, requestId);
                    if (requestId != requestGeneration.get()) {
                        resolveCancelled(call, language);
                        return;
                    }

                    JSObject result = new JSObject();
                    result.put("queued", true);
                    result.put("language", "en-US");
                    result.put("offline", true);
                    result.put("engine", "Sherpa-ONNX Piper INT8");
                    result.put("voice", VOICE_NAME);
                    call.resolve(result);
                } catch (Throwable error) {
                    lastError = safeMessage(error);
                    Log.e(TAG, "Offline English synthesis failed", error);
                    call.reject("offline_speech_failed: " + safeMessage(error));
                }
            });
        } catch (Exception error) {
            call.reject("speech_queue_unavailable: " + safeMessage(error));
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        requestGeneration.incrementAndGet();
        stopActiveTrack();
        JSObject result = new JSObject();
        result.put("stopped", true);
        call.resolve(result);
    }

    private void ensureEngine() throws IOException {
        if (engine != null) return;
        synchronized (engineLock) {
            if (engine != null) return;
            loading = true;
            progress = 3;
            lastError = "";
            try {
                File root = new File(getContext().getFilesDir(), MODEL_RELATIVE_DIR);
                if (!isInstalledModelValid(root)) {
                    if (root.exists()) deleteRecursively(root);
                    if (!root.mkdirs()) throw new IOException("cannot_create_model_directory");
                    extractBundledModel(root);
                    progress = 88;
                    validateModelFiles(root, true);
                    writeInstalledMarker(root);
                }

                progress = 94;
                OfflineTts newEngine = createEngine(root);
                engine = newEngine;
                progress = 100;
                cleanupLegacyModelDirectories(root.getParentFile());
                Log.i(TAG, "Bundled English Piper INT8 voice is ready");
            } catch (IOException | RuntimeException error) {
                lastError = safeMessage(error);
                progress = 0;
                throw error;
            } finally {
                loading = false;
            }
        }
    }

    private boolean isInstalledModelValid(File root) {
        try {
            File marker = new File(root, ".installed");
            if (!marker.isFile()) return false;
            byte[] markerBytes = readSmallFile(marker, 256);
            String markerValue = new String(markerBytes, StandardCharsets.UTF_8).trim();
            if (!MODEL_SHA256.equals(markerValue)) return false;
            validateModelFiles(root, true);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void extractBundledModel(File destination) throws IOException {
        long extractedBytes = 0;
        int entryCount = 0;
        byte[] buffer = new byte[64 * 1024];
        AssetManager assets = getContext().getAssets();

        try (InputStream asset = assets.open(ASSET_NAME, AssetManager.ACCESS_STREAMING);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(asset))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ARCHIVE_ENTRIES) throw new IOException("voice_archive_entry_limit_exceeded");
                String name = entry.getName();
                if (name == null || name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains("../")) {
                    throw new IOException("unsafe_voice_archive_path");
                }

                File output = new File(destination, name);
                String base = destination.getCanonicalPath() + File.separator;
                if (!output.getCanonicalPath().startsWith(base)) throw new IOException("unsafe_voice_archive_path");

                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs()) throw new IOException("cannot_create_voice_directory");
                } else {
                    File parent = output.getParentFile();
                    if (parent == null || (!parent.exists() && !parent.mkdirs())) throw new IOException("cannot_create_voice_directory");
                    try (BufferedOutputStream file = new BufferedOutputStream(new FileOutputStream(output))) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            extractedBytes += count;
                            if (extractedBytes > MAX_EXTRACTED_BYTES) throw new IOException("voice_archive_uncompressed_limit_exceeded");
                            file.write(buffer, 0, count);
                        }
                    }
                }
                zip.closeEntry();
                progress = Math.min(80, 8 + (int) (extractedBytes * 72 / MAX_EXTRACTED_BYTES));
            }
        } catch (IOException error) {
            deleteRecursively(destination);
            throw new IOException("bundled_voice_asset_unavailable_or_invalid: " + safeMessage(error), error);
        }
    }

    private void validateModelFiles(File root, boolean verifyHash) throws IOException {
        File model = new File(root, MODEL_NAME);
        if (!model.isFile() || model.length() != MODEL_SIZE_BYTES) throw new IOException("english_model_missing_or_wrong_size");
        for (String relative : new String[] {
                "tokens.txt",
                "espeak-ng-data/en_dict",
                "espeak-ng-data/phondata",
                "espeak-ng-data/phondata-manifest",
                "espeak-ng-data/phonindex",
                "espeak-ng-data/phontab"}) {
            File required = new File(root, relative);
            if (!required.isFile() || required.length() == 0) throw new IOException("voice_file_missing: " + relative);
        }
        if (verifyHash && !MODEL_SHA256.equals(sha256(model))) throw new IOException("english_model_sha256_mismatch");
    }

    private void writeInstalledMarker(File root) throws IOException {
        File marker = new File(root, ".installed");
        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write((MODEL_SHA256 + "\n").getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    private OfflineTts createEngine(File root) {
        OfflineTtsVitsModelConfig vits = OfflineTtsVitsModelConfig.builder()
            .setModel(new File(root, MODEL_NAME).getAbsolutePath())
            .setTokens(new File(root, "tokens.txt").getAbsolutePath())
            .setDataDir(new File(root, "espeak-ng-data").getAbsolutePath())
            .build();
        OfflineTtsModelConfig modelConfig = OfflineTtsModelConfig.builder()
            .setVits(vits)
            .setNumThreads(2)
            .setDebug(false)
            .setProvider("cpu")
            .build();
        return new OfflineTts(OfflineTtsConfig.builder()
            .setModel(modelConfig)
            .setMaxNumSentences(1)
            .build());
    }

    private void playAudio(GeneratedAudio audio, long requestId) throws IOException, InterruptedException {
        if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0 || audio.getSampleRate() < 8000) {
            throw new IOException("tts_returned_empty_audio");
        }
        float[] samples = audio.getSamples();
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float value = Math.max(-1.0f, Math.min(1.0f, samples[i]));
            pcm[i] = (short) (value * 32767.0f);
        }

        int sampleRate = audio.getSampleRate();
        int minimumBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );
        if (minimumBuffer <= 0) throw new IOException("audio_track_buffer_unavailable");

        long requestedBuffer = Math.min((long) pcm.length * 2L, (long) minimumBuffer * 8L);
        int bufferBytes = (int) Math.max((long) minimumBuffer, requestedBuffer);
        AudioTrack track;
        if (Build.VERSION.SDK_INT >= 23) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
            AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
            track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        } else {
            track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
                AudioTrack.MODE_STREAM
            );
        }

        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new IOException("audio_track_not_initialized");
        }

        activeTrack = track;
        try {
            if (requestId != requestGeneration.get()) return;
            track.play();
            int offset = 0;
            while (offset < pcm.length && requestId == requestGeneration.get()) {
                int count = Math.min(pcm.length - offset, 4096);
                int written = track.write(pcm, offset, count);
                if (written < 0) throw new IOException("audio_track_write_error_" + written);
                if (written == 0) {
                    Thread.sleep(10);
                    continue;
                }
                offset += written;
            }
            while (requestId == requestGeneration.get() && track.getPlaybackHeadPosition() < pcm.length) {
                Thread.sleep(20);
            }
        } finally {
            if (activeTrack == track) activeTrack = null;
            try { track.pause(); } catch (Exception ignored) { }
            try { track.flush(); } catch (Exception ignored) { }
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
        }
    }

    private void stopActiveTrack() {
        AudioTrack track = activeTrack;
        if (track == null) return;
        try { track.pause(); } catch (Exception ignored) { }
        try { track.flush(); } catch (Exception ignored) { }
        try { track.stop(); } catch (Exception ignored) { }
    }

    private void cleanupLegacyModelDirectories(File parent) {
        if (parent == null || !parent.isDirectory()) return;
        for (String name : new String[] {"fa", "en"}) {
            File legacy = new File(parent, name);
            if (legacy.isDirectory()) deleteRecursively(legacy);
        }
    }

    private static boolean containsPersian(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u0600' && c <= '\u06FF') || (c >= '\u0750' && c <= '\u077F') || (c >= '\u08A0' && c <= '\u08FF')) return true;
        }
        return false;
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("sha256_not_available", error);
        }
    }

    private static byte[] readSmallFile(File file, int maxBytes) throws IOException {
        if (file.length() > maxBytes) throw new IOException("marker_file_too_large");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buffer.length) {
                int count = input.read(buffer, offset, buffer.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != buffer.length) throw new IOException("marker_read_failed");
            return buffer;
        }
    }

    private static void resolveCancelled(PluginCall call, String language) {
        JSObject result = new JSObject();
        result.put("cancelled", true);
        result.put("language", language == null ? "en-US" : language);
        result.put("offline", true);
        call.resolve(result);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) Log.w(TAG, "Could not delete " + file.getAbsolutePath());
    }
}
