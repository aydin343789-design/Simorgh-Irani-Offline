package ir.simorgh.irani;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;
import java.util.Set;

@CapacitorPlugin(name = "SimorghSpeech")
public class SimorghSpeechPlugin extends Plugin {
    private TextToSpeech textToSpeech;
    private volatile boolean initialized = false;

    @Override
    public void load() {
        textToSpeech = new TextToSpeech(getContext(), status -> {
            initialized = status == TextToSpeech.SUCCESS;
        });
    }

    @Override
    protected void handleOnDestroy() {
        initialized = false;
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.handleOnDestroy();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject result = new JSObject();
        result.put("ready", initialized);
        result.put("englishOffline", hasOfflineVoice("en-US"));
        result.put("persianOffline", hasOfflineVoice("fa-IR"));
        call.resolve(result);
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        String language = call.getString("lang", "en-US");
        Double requestedRate = call.getDouble("rate", 0.85);

        if (text == null || text.trim().isEmpty()) {
            call.reject("empty_text");
            return;
        }
        if (!initialized || textToSpeech == null) {
            call.reject("speech_engine_not_ready");
            return;
        }

        Voice voice = findOfflineVoice(language);
        if (voice == null) {
            call.reject("offline_voice_unavailable:" + language);
            return;
        }
        if (textToSpeech.setVoice(voice) != TextToSpeech.SUCCESS) {
            call.reject("offline_voice_selection_failed:" + language);
            return;
        }

        float rate = requestedRate == null ? 0.85f : requestedRate.floatValue();
        rate = Math.max(0.5f, Math.min(rate, 1.5f));
        textToSpeech.setSpeechRate(rate);
        int status = textToSpeech.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "simorgh_" + System.currentTimeMillis()
        );
        if (status == TextToSpeech.ERROR) {
            call.reject("offline_speech_failed");
            return;
        }

        JSObject result = new JSObject();
        result.put("queued", true);
        result.put("language", language);
        result.put("offline", true);
        call.resolve(result);
    }

    private boolean hasOfflineVoice(String languageTag) {
        return findOfflineVoice(languageTag) != null;
    }

    private Voice findOfflineVoice(String languageTag) {
        if (!initialized || textToSpeech == null) {
            return null;
        }
        Locale target = Locale.forLanguageTag(languageTag);
        Set<Voice> voices = textToSpeech.getVoices();
        if (voices == null) {
            return null;
        }
        for (Voice voice : voices) {
            Locale candidate = voice.getLocale();
            if (candidate != null
                && target.getLanguage().equalsIgnoreCase(candidate.getLanguage())
                && !voice.isNetworkConnectionRequired()) {
                return voice;
            }
        }
        return null;
    }
}
