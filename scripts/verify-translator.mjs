import { access, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const fileExists = async (path) => {
  try { await access(path); return true; } catch { return false; }
};
const html = await readFile(resolve(root, 'www/index.html'), 'utf8');
const bridge = await readFile(
  resolve(root, 'android/app/src/main/java/ir/simorgh/irani/SimorghTranslatorBridge.java'),
  'utf8',
);
const speech = await readFile(
  resolve(root, 'android/app/src/main/java/ir/simorgh/irani/SimorghSpeechBridge.java'),
  'utf8',
);
const gradle = await readFile(resolve(root, 'android/app/build.gradle'), 'utf8');

const checks = [
  ['ML Kit dependency', gradle.includes("com.google.mlkit:translate:17.0.3")],
  ['native translator bridge', bridge.includes('class SimorghTranslatorBridge')],
  ['embedded speech bridge', speech.includes('class SimorghSpeechBridge')],
  ['embedded Piper asset', await fileExists(resolve(root, 'android/app/src/main/assets/tts/tts-en.zip'))],
  ['no Android/browser TTS API', !html.includes('window.speechSynthesis') && !html.includes('SpeechSynthesisUtterance')],
  ['English-only speech route', speech.includes('embedded_english_voice_only')],
  ['Wi-Fi-only model download', bridge.includes('.requireWifi()')],
  ['native result callback', bridge.includes('window.__nativeTranslatorCallback')],
  ['explicit model confirmation', html.includes('window.confirm(')],
  ['native-first sentence translation', html.indexOf('await translateWithNativeEngine') < html.indexOf('const localResult=lookupLocalTranslation')],
  ['mixed-result guard', html.includes('function isCompleteLocalTranslation')],
  ['no Maven Sherpa dependency', !gradle.includes('sherpa-onnx')],
];

const failed = checks.filter(([, passed]) => !passed).map(([name]) => name);
for (const [name, passed] of checks) {
  console.log(`${passed ? 'PASS' : 'FAIL'} ${name}`);
}
if (failed.length) {
  throw new Error(`Translator integrity check failed: ${failed.join(', ')}`);
}
