package ir.simorgh.irani;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends BridgeActivity {
    private AndroidTranslatorBridge translatorBridge;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        attachTranslatorBridge();
    }

    @Override
    public void onResume() {
        super.onResume();
        attachTranslatorBridge();
        if (translatorBridge != null) {
            translatorBridge.refreshStatus();
        }
    }

    @Override
    protected void onDestroy() {
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().removeJavascriptInterface("AndroidTranslator");
        }
        if (translatorBridge != null) {
            translatorBridge.close();
            translatorBridge = null;
        }
        super.onDestroy();
    }

    private void attachTranslatorBridge() {
        if (getBridge() == null || getBridge().getWebView() == null) {
            return;
        }
        WebView webView = getBridge().getWebView();
        if (translatorBridge == null) {
            translatorBridge = new AndroidTranslatorBridge(this, webView);
            webView.addJavascriptInterface(translatorBridge, "AndroidTranslator");
        }
    }
}

final class AndroidTranslatorBridge {
    private final Activity activity;
    private final WebView webView;
    private final RemoteModelManager modelManager = RemoteModelManager.getInstance();
    private final TranslateRemoteModel persianModel =
            new TranslateRemoteModel.Builder(TranslateLanguage.PERSIAN).build();
    private final AtomicBoolean preparing = new AtomicBoolean(false);

    private Translator faToEn;
    private Translator enToFa;
    private volatile boolean ready = false;

    AndroidTranslatorBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        createTranslators();
    }

    private void createTranslators() {
        TranslatorOptions faToEnOptions = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.PERSIAN)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build();
        TranslatorOptions enToFaOptions = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.PERSIAN)
                .build();

        faToEn = Translation.getClient(faToEnOptions);
        enToFa = Translation.getClient(enToFaOptions);
    }

    @JavascriptInterface
    public boolean isReady() {
        return ready;
    }

    @JavascriptInterface
    public void refreshStatus() {
        modelManager.isModelDownloaded(persianModel)
                .addOnSuccessListener(downloaded -> {
                    ready = Boolean.TRUE.equals(downloaded);
                    if (ready) {
                        postStatus("مدل ترجمه فارسی آماده است؛ ترجمه کاملاً آفلاین است", true, false);
                    } else {
                        postStatus("مدل ترجمه روی گوشی نصب نیست؛ برای فعال‌سازی یک‌بار اینترنت لازم است", false, false);
                    }
                })
                .addOnFailureListener(e -> {
                    ready = false;
                    postStatus("بررسی مدل ترجمه انجام نشد؛ دوباره تلاش کن", false, true);
                });
    }

    @JavascriptInterface
    public void prepareModels() {
        if (!preparing.compareAndSet(false, true)) {
            return;
        }

        postStatus("در حال دریافت مدل ترجمه فارسی...", false, false);

        DownloadConditions conditions = new DownloadConditions.Builder().build();
        modelManager.download(persianModel, conditions)
                .addOnSuccessListener(unused -> {
                    ready = true;
                    preparing.set(false);
                    postStatus("مدل ترجمه فارسی نصب شد؛ ترجمه اکنون بدون اینترنت کار می‌کند", true, false);
                })
                .addOnFailureListener(e -> {
                    ready = false;
                    preparing.set(false);
                    postStatus("دریافت مدل ترجمه ناموفق بود؛ اتصال اینترنت را بررسی کن", false, true);
                });
    }

    @JavascriptInterface
    public void translateAsync(String text, String from, String to, String requestId) {
        if (!ready) {
            postResult(requestId, "", "translator-not-ready");
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            postResult(requestId, "", "empty-text");
            return;
        }

        final Translator translator;
        if ("fa".equals(from) && "en".equals(to)) {
            translator = faToEn;
        } else if ("en".equals(from) && "fa".equals(to)) {
            translator = enToFa;
        } else {
            postResult(requestId, "", "unsupported-language-pair");
            return;
        }

        translator.translate(text.trim())
                .addOnSuccessListener(result -> postResult(requestId, result, ""))
                .addOnFailureListener(e -> {
                    ready = false;
                    postResult(requestId, "", "translation-failed");
                    postStatus("ترجمه انجام نشد؛ وضعیت مدل را دوباره بررسی کن", false, true);
                });
    }

    void close() {
        if (faToEn != null) {
            faToEn.close();
            faToEn = null;
        }
        if (enToFa != null) {
            enToFa.close();
            enToFa = null;
        }
    }

    private void postStatus(String message, boolean isReady, boolean error) {
        String script = "window.nativeTranslatorStatus && window.nativeTranslatorStatus("
                + jsQuote(message) + "," + isReady + "," + error + ");";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private void postResult(String id, String result, String error) {
        String script = "window.__nativeTranslatorCallback && window.__nativeTranslatorCallback("
                + jsQuote(id) + "," + jsQuote(result) + "," + jsQuote(error) + ");";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private String jsQuote(String value) {
        if (value == null) {
            value = "";
        }
        return org.json.JSONObject.quote(value);
    }
}
