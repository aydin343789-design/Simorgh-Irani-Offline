package ir.simorgh.irani;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Narrow JavaScript bridge used only by the bundled Capacitor WebView.
 *
 * ML Kit models are deliberately downloaded only after a user action in the
 * web UI, using any available network connection. Once both models are installed, translation is entirely
 * on-device. This bridge exposes no filesystem, intent, or arbitrary-code API.
 */
public final class SimorghTranslatorBridge {
    private static final String PERSIAN = "fa";
    private static final String ENGLISH = "en";
    private static final int MAX_INPUT_LENGTH = 5_000;

    private final WebView webView;
    private final RemoteModelManager modelManager;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private volatile boolean persianInstalled;
    private volatile boolean englishInstalled;
    private volatile boolean modelsChecked;
    private volatile boolean downloading;

    public SimorghTranslatorBridge(WebView webView) {
        this.webView = webView;
        this.modelManager = RemoteModelManager.getInstance();
    }

    @JavascriptInterface
    public boolean isReady() {
        return persianInstalled && englishInstalled;
    }

    @JavascriptInterface
    public void refreshStatus() {
        if (destroyed.get()) {
            return;
        }
        refreshInstalledModels(true);
    }

    /**
     * Starts the one-time, user-confirmed download using the currently available
     * network (Wi-Fi or mobile data).
     */
    @JavascriptInterface
    public void prepareModels() {
        if (destroyed.get() || downloading) {
            return;
        }
        if (isReady()) {
            emitStatus("مدل‌های فارسی و انگلیسی از قبل نصب‌اند؛ ترجمه بدون اینترنت آماده است.", true, false);
            return;
        }
        downloading = true;
        emitStatus("در حال دریافت مدل‌های فارسی و انگلیسی با اینترنت موجود…", false, false);

        DownloadConditions anyNetwork = new DownloadConditions.Builder()
                .build();
        Task<Void> persianTask = modelManager.download(persianModel(), anyNetwork);
        Task<Void> englishTask = modelManager.download(englishModel(), anyNetwork);

        Tasks.whenAllComplete(persianTask, englishTask).addOnCompleteListener(ignored -> {
            downloading = false;
            if (persianTask.isSuccessful() && englishTask.isSuccessful()) {
                refreshInstalledModels(true);
                return;
            }
            emitStatus("دریافت مدل ترجمه کامل نشد: " + downloadError(persianTask, englishTask)
                    + ". اتصال اینترنت و Google Play services را بررسی کن.", false, true);
        });
    }

    @JavascriptInterface
    public void translateAsync(String text, String from, String to, String callbackId) {
        if (destroyed.get()) {
            return;
        }
        if (callbackId == null || callbackId.trim().isEmpty()) {
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            emitCallback(callbackId, "", "empty_text");
            return;
        }
        if (text.length() > MAX_INPUT_LENGTH) {
            emitCallback(callbackId, "", "input_too_long");
            return;
        }
        if (!isSupportedLanguage(from) || !isSupportedLanguage(to) || from.equals(to)) {
            emitCallback(callbackId, "", "unsupported_language");
            return;
        }
        if (!modelsChecked) {
            modelManager.getDownloadedModels(TranslateRemoteModel.class)
                    .addOnSuccessListener(models -> {
                        updateInstalledModels(models);
                        modelsChecked = true;
                        translateAsync(text, from, to, callbackId);
                    })
                    .addOnFailureListener(error -> emitCallback(callbackId, "", "models_check_failed"));
            return;
        }
        if (!isReady()) {
            refreshInstalledModels(false);
            emitCallback(callbackId, "", "models_not_downloaded");
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(from)
                .setTargetLanguage(to)
                .build();
        Translator translator = Translation.getClient(options);
        translator.translate(text.trim())
                .addOnSuccessListener(result -> {
                    translator.close();
                    emitCallback(callbackId, result, "");
                })
                .addOnFailureListener(error -> {
                    translator.close();
                    emitCallback(callbackId, "", "translation_failed:" + safeMessage(error));
                });
    }

    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            webView.removeJavascriptInterface("AndroidTranslator");
        }
    }

    private void refreshInstalledModels(boolean reportStatus) {
        modelManager.getDownloadedModels(TranslateRemoteModel.class)
                .addOnSuccessListener(models -> {
                    updateInstalledModels(models);
                    modelsChecked = true;
                    if (!reportStatus || downloading) {
                        return;
                    }
                    if (isReady()) {
                        emitStatus("مدل‌های فارسی و انگلیسی نصب‌اند؛ ترجمه بدون اینترنت انجام می‌شود.", true, false);
                    } else {
                        emitStatus("مدل ترجمه نصب نشده است؛ برای فعال‌سازی، یک‌بار با اینترنت موجود مدل‌ها را دریافت کن.", false, false);
                    }
                })
                .addOnFailureListener(error -> {
                    if (reportStatus) {
                        emitStatus("وضعیت مدل ترجمه قابل بررسی نیست؛ Google Play services را به‌روز کن.", false, true);
                    }
                });
    }

    private void updateInstalledModels(Set<TranslateRemoteModel> models) {
        persianInstalled = models.contains(persianModel());
        englishInstalled = models.contains(englishModel());
    }

    private static TranslateRemoteModel persianModel() {
        return new TranslateRemoteModel.Builder(PERSIAN).build();
    }

    private static TranslateRemoteModel englishModel() {
        return new TranslateRemoteModel.Builder(ENGLISH).build();
    }

    private static boolean isSupportedLanguage(String language) {
        return PERSIAN.equals(language) || ENGLISH.equals(language);
    }

    private void emitStatus(String message, boolean ready, boolean error) {
        if (destroyed.get()) {
            return;
        }
        String script = "if (typeof window.nativeTranslatorStatus === 'function') { window.nativeTranslatorStatus("
                + JSONObject.quote(message) + "," + ready + "," + error + "); }";
        runJavascript(script);
    }

    private void emitCallback(String callbackId, String result, String error) {
        if (destroyed.get() || callbackId == null || callbackId.isEmpty()) {
            return;
        }
        String script = "if (typeof window.__nativeTranslatorCallback === 'function') { window.__nativeTranslatorCallback("
                + JSONObject.quote(callbackId) + ","
                + JSONObject.quote(result == null ? "" : result) + ","
                + JSONObject.quote(error == null ? "" : error) + "); }";
        runJavascript(script);
    }

    private void runJavascript(String script) {
        webView.post(() -> {
            if (!destroyed.get()) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private static String safeMessage(Exception error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return "unknown_error";
        }
        return error.getMessage().replace("\n", " ").replace("\r", " ");
    }

    private static String downloadError(Task<Void> persianTask, Task<Void> englishTask) {
        if (!persianTask.isSuccessful()) return "فارسی: " + safeMessage(persianTask.getException());
        if (!englishTask.isSuccessful()) return "انگلیسی: " + safeMessage(englishTask.getException());
        return "خطای نامشخص";
    }
}
