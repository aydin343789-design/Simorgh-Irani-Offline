package ir.simorgh.irani;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private SimorghTranslatorBridge translatorBridge;
    private SimorghSpeechBridge speechBridge;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        translatorBridge = new SimorghTranslatorBridge(getBridge().getWebView());
        getBridge().getWebView().addJavascriptInterface(translatorBridge, "AndroidTranslator");
        speechBridge = new SimorghSpeechBridge(getBridge().getWebView());
        getBridge().getWebView().addJavascriptInterface(speechBridge, "AndroidSpeech");
    }

    @Override
    public void onDestroy() {
        if (translatorBridge != null) {
            translatorBridge.destroy();
        }
        if (speechBridge != null) {
            speechBridge.destroy();
        }
        super.onDestroy();
    }
}
