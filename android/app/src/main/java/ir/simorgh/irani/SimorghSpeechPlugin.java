package ir.simorgh.irani;

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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "SimorghSpeech")
public class SimorghSpeechPlugin extends Plugin {
    private static final String TAG = "SimorghSpeech";
    private static final String ASSET_TAG = "tts-assets-v2";
    private static final String BASE_URL = "https://github.com/aydin343789-design/Simorgh-Irani-Offline/releases/download/" + ASSET_TAG + "/";
    private static final String FA_URL = BASE_URL + "tts-fa.zip";
    private static final String EN_URL = BASE_URL + "tts-en.zip";

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Map<String, OfflineTts> engines = new HashMap<>();
    private final Map<String, Object> modelLocks = new HashMap<>();
    private volatile boolean faDownloading;
    private volatile boolean enDownloading;
    private volatile String faError = "";
    private volatile String enError = "";

    public SimorghSpeechPlugin() {
        modelLocks.put("fa", new Object());
        modelLocks.put("en", new Object());
    }

    @Override
    public void load() {
        super.load();
        executor.execute(() -> ensureModel("fa"));
        executor.execute(() -> ensureModel("en"));
    }

    @Override
    protected void handleOnDestroy() {
        synchronized (engines) {
            for (OfflineTts engine : engines.values()) {
                try { engine.release(); } catch (Exception ignored) { }
            }
            engines.clear();
        }
        executor.shutdownNow();
        super.handleOnDestroy();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject result = new JSObject();
        result.put("ready", isReady("fa") || isReady("en"));
        result.put("embedded", true);
        result.put("engine", "sherpa-onnx-piper-v1.13.8");
        result.put("englishOffline", isReady("en"));
        result.put("persianOffline", isReady("fa"));
        result.put("englishDownloading", enDownloading);
        result.put("persianDownloading", faDownloading);
        result.put("englishError", enError);
        result.put("persianError", faError);
        call.resolve(result);
    }

    @PluginMethod
    public void downloadModels(PluginCall call) {
        executor.execute(() -> {
            ensureModel("fa");
            ensureModel("en");
            JSObject result = new JSObject();
            result.put("englishOffline", isReady("en"));
            result.put("persianOffline", isReady("fa"));
            result.put("englishError", enError);
            result.put("persianError", faError);
            call.resolve(result);
        });
    }

    @PluginMethod
    public void speak(PluginCall call) {
        final String text = call.getString("text", "");
        final String language = call.getString("lang", "en-US");
        final Double requestedRate = call.getDouble("rate", 0.85);
        if (text == null || text.trim().isEmpty()) {
            call.reject("empty_text");
            return;
        }
        final String languageKey = language.toLowerCase().startsWith("fa") ? "fa" : "en";
        final float rate = requestedRate == null ? 0.85f : Math.max(0.5f, Math.min(requestedRate.floatValue(), 1.5f));
        executor.execute(() -> {
            try {
                ensureModel(languageKey);
                OfflineTts engine;
                synchronized (engines) { engine = engines.get(languageKey); }
                if (engine == null) {
                    call.reject("embedded_voice_not_ready:" + language + ":" + getError(languageKey));
                    return;
                }
                GeneratedAudio audio = engine.generate(text, 0, 1.0f / rate);
                play(audio);
                JSObject result = new JSObject();
                result.put("queued", true);
                result.put("language", language);
                result.put("offline", true);
                result.put("engine", "sherpa-onnx-piper-v1.13.8");
                call.resolve(result);
            } catch (Throwable error) {
                Log.e(TAG, "embedded TTS failed for " + languageKey, error);
                call.reject("embedded_speech_failed:" + safeMessage(error));
            }
        });
    }

    private boolean isReady(String language) {
        synchronized (engines) { return engines.containsKey(language); }
    }

    private String getError(String language) { return "fa".equals(language) ? faError : enError; }

    private void ensureModel(String language) {
        if (isReady(language)) return;
        Object lock = modelLocks.get(language);
        synchronized (lock) {
            if (isReady(language)) return;
            File root = new File(getContext().getFilesDir(), "simorgh-tts/" + language);
            File marker = new File(root, ".installed");
            try {
                if (!isModelComplete(root, language)) {
                    setDownloading(language, true);
                    downloadAndExtract("fa".equals(language) ? FA_URL : EN_URL, root);
                    if (!isModelComplete(root, language)) throw new IOException("model_files_missing");
                    if (!marker.exists() && !marker.createNewFile()) throw new IOException("cannot_mark_model_installed");
                }
                OfflineTts engine = createEngine(root, language);
                synchronized (engines) {
                    if (!engines.containsKey(language)) engines.put(language, engine);
                    else engine.release();
                }
                setError(language, "");
                Log.i(TAG, "ready: " + language + " using " + root.getAbsolutePath());
            } catch (Throwable error) {
                setError(language, safeMessage(error));
                Log.e(TAG, "model init failed for " + language, error);
                if (marker.exists()) marker.delete();
            } finally { setDownloading(language, false); }
        }
    }

    private boolean isModelComplete(File root, String language) {
        String model = "fa".equals(language) ? "fa-haaniye_low.onnx" : "en_US-hfc_female-medium.onnx";
        return new File(root, model).isFile()
            && new File(root, "tokens.txt").isFile()
            && new File(root, "espeak-ng-data/phondata").isFile();
    }

    private void setDownloading(String language, boolean value) { if ("fa".equals(language)) faDownloading = value; else enDownloading = value; }
    private void setError(String language, String value) { if ("fa".equals(language)) faError = value; else enError = value; }

    private OfflineTts createEngine(File root, String language) {
        String model = new File(root, "fa".equals(language) ? "fa-haaniye_low.onnx" : "en_US-hfc_female-medium.onnx").getAbsolutePath();
        OfflineTtsVitsModelConfig vits = OfflineTtsVitsModelConfig.builder()
            .setModel(model).setTokens(new File(root, "tokens.txt").getAbsolutePath())
            .setDataDir(new File(root, "espeak-ng-data").getAbsolutePath()).build();
        OfflineTtsModelConfig modelConfig = OfflineTtsModelConfig.builder()
            .setVits(vits).setNumThreads(2).setDebug(false).setProvider("cpu").build();
        return new OfflineTts(OfflineTtsConfig.builder().setModel(modelConfig).setMaxNumSentences(1).build());
    }

    private void downloadAndExtract(String urlString, File root) throws IOException {
        File parent = root.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("cannot_create_model_directory");
        File zip = new File(parent, root.getName() + ".download");
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(30000); connection.setReadTimeout(180000);
        connection.setInstanceFollowRedirects(true); connection.setRequestProperty("User-Agent", "Simorgh-Irani-Offline");
        int response = connection.getResponseCode();
        if (response < 200 || response >= 300) throw new IOException("model_download_http_" + response);
        try (InputStream input = new BufferedInputStream(connection.getInputStream()); FileOutputStream output = new FileOutputStream(zip)) {
            byte[] buffer = new byte[1024 * 64]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } finally { connection.disconnect(); }
        if (root.exists()) deleteRecursively(root);
        if (!root.mkdirs()) throw new IOException("cannot_create_extracted_model_directory");
        unzip(zip, root);
        if (!zip.delete()) zip.deleteOnExit();
    }

    private void unzip(File zipFile, File destination) throws IOException {
        try (java.util.zip.ZipInputStream input = new java.util.zip.ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            java.util.zip.ZipEntry entry; byte[] buffer = new byte[1024 * 64];
            while ((entry = input.getNextEntry()) != null) {
                File output = new File(destination, entry.getName());
                String base = destination.getCanonicalPath() + File.separator;
                if (!output.getCanonicalPath().startsWith(base)) throw new IOException("unsafe_model_archive");
                if (entry.isDirectory()) { if (!output.exists() && !output.mkdirs()) throw new IOException("mkdir_failed"); }
                else {
                    File parent = output.getParentFile(); if (!parent.exists() && !parent.mkdirs()) throw new IOException("mkdir_failed");
                    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(output))) {
                        int count; while ((count = input.read(buffer)) != -1) stream.write(buffer, 0, count);
                    }
                }
                input.closeEntry();
            }
        }
    }

    private void play(GeneratedAudio audio) throws IOException, InterruptedException {
        float[] samples = audio.getSamples();
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) pcm[i] = (short) (Math.max(-1f, Math.min(1f, samples[i])) * 32767f);
        int minBuffer = AudioTrack.getMinBufferSize(audio.getSampleRate(), AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) throw new IOException("audio_track_buffer_unavailable");
        AudioTrack track;
        if (Build.VERSION.SDK_INT >= 23) {
            track = new AudioTrack.Builder().setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(audio.getSampleRate()).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(minBuffer, Math.min(pcm.length * 2, minBuffer * 8)))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
        } else {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, audio.getSampleRate(), AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuffer, minBuffer * 2), AudioTrack.MODE_STREAM);
        }
        if (track.getState() != AudioTrack.STATE_INITIALIZED) { track.release(); throw new IOException("audio_track_not_initialized"); }
        try {
            track.play();
            int offset = 0;
            while (offset < pcm.length) { int count = Math.min(pcm.length - offset, 4096); int written = track.write(pcm, offset, count); if (written < 0) throw new IOException("audio_track_write_" + written); offset += written; }
        } finally { try { track.stop(); } catch (Exception ignored) { } track.release(); }
    }

    private static String safeMessage(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private static void deleteRecursively(File file) { if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteRecursively(child); } file.delete(); }
}
