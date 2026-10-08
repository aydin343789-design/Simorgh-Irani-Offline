package ir.simorgh.irani;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@CapacitorPlugin(name = "SimorghTranslation")
public class SimorghTranslationPlugin extends Plugin {
    private static final String EN = TranslateLanguage.ENGLISH;
    private static final String FA = TranslateLanguage.PERSIAN;
    private final Map<String, Translator> translators = new HashMap<>();

    @PluginMethod
    public void getStatus(PluginCall call) {
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel.class)
            .addOnSuccessListener(models -> {
                Set<String> languages = new HashSet<>();
                for (TranslateRemoteModel model : models) languages.add(model.getLanguage());
                boolean persianReady = languages.contains(FA);
                JSObject result = statusObject(persianReady, true, persianReady);
                call.resolve(result);
            })
            .addOnFailureListener(error -> call.reject("model_status_failed: " + safeMessage(error)));
    }

    @PluginMethod
    public void downloadModels(PluginCall call) {
        DownloadConditions conditions = new DownloadConditions.Builder().requireWifi().build();
        TranslateRemoteModel persian = new TranslateRemoteModel.Builder(FA).build();

        RemoteModelManager manager = RemoteModelManager.getInstance();
        manager.download(persian, conditions)
            .addOnSuccessListener(unused -> {
                JSObject result = statusObject(true, true, true);
                result.put("downloaded", true);
                call.resolve(result);
            })
            .addOnFailureListener(error -> call.reject("model_download_failed: " + safeMessage(error)));
    }

    @PluginMethod
    public void translate(PluginCall call) {
        String text = call.getString("text", "");
        String from = normalizeLanguage(call.getString("from", "en"));
        String to = normalizeLanguage(call.getString("to", "fa"));
        if (text == null || text.trim().isEmpty()) {
            call.reject("empty_text");
            return;
        }
        if (!((EN.equals(from) || FA.equals(from)) && (EN.equals(to) || FA.equals(to))) || from.equals(to)) {
            call.reject("unsupported_language_pair");
            return;
        }
        if (text.length() > 5000) {
            call.reject("text_too_long_max_5000_characters");
            return;
        }

        TranslateRemoteModel persianModel = new TranslateRemoteModel.Builder(FA).build();
        RemoteModelManager.getInstance().isModelDownloaded(persianModel)
            .addOnSuccessListener(downloaded -> {
                if (!downloaded) {
                    call.reject("persian_model_not_installed_download_after_user_consent");
                    return;
                }
                runTranslation(call, text.trim(), from, to);
            })
            .addOnFailureListener(error -> call.reject("model_status_failed: " + safeMessage(error)));
    }

    private void runTranslation(PluginCall call, String text, String from, String to) {
        Translator translator;
        synchronized (translators) {
            String key = from + ">" + to;
            translator = translators.get(key);
            if (translator == null) {
                TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(from)
                    .setTargetLanguage(to)
                    .build();
                translator = Translation.getClient(options);
                translators.put(key, translator);
            }
        }

        translator.translate(text.trim())
            .addOnSuccessListener(result -> {
                if (result == null || result.trim().isEmpty()) {
                    call.reject("empty_translation_result");
                    return;
                }
                JSObject response = new JSObject();
                response.put("text", result.trim());
                response.put("from", from);
                response.put("to", to);
                response.put("offline", true);
                response.put("engine", "Google ML Kit on-device translation");
                call.resolve(response);
            })
            .addOnFailureListener(error -> call.reject("translation_failed: " + safeMessage(error)));
    }

    @Override
    protected void handleOnDestroy() {
        synchronized (translators) {
            for (Translator translator : translators.values()) {
                try { translator.close(); } catch (Exception ignored) { }
            }
            translators.clear();
        }
        super.handleOnDestroy();
    }

    private static String normalizeLanguage(String language) {
        if (language == null) return "";
        String value = language.toLowerCase(Locale.ROOT);
        if (value.startsWith("en")) return EN;
        if (value.startsWith("fa") || value.startsWith("per")) return FA;
        return value;
    }

    private static JSObject statusObject(boolean ready, boolean english, boolean persian) {
        JSObject result = new JSObject();
        result.put("ready", ready);
        result.put("englishDownloaded", english);
        result.put("persianDownloaded", persian);
        result.put("offlineAfterDownload", true);
        result.put("engine", "Google ML Kit on-device translation");
        result.put("modelSizeEstimateMb", 30);
        result.put("englishBuiltIn", true);
        return result;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
