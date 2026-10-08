package ir.simorgh.irani;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SimorghSpeechPlugin.class);
        registerPlugin(SimorghTranslationPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
