import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import JavaScriptObfuscator from 'javascript-obfuscator';
import { parse } from 'acorn';

const root = resolve(import.meta.dirname, '..');
const sourcePath = resolve(root, 'www/index.html');
const outputPath = resolve(root, 'dist/index.html');
const source = await readFile(sourcePath, 'utf8');
const handlers = [...source.matchAll(/\bon[a-z]+\s*=\s*(["'])(.*?)\1/gi)].map((match) => match[2]).join('\n');
const handlerTokens = new Set(handlers.match(/[A-Za-z_$][\w$]*/g) ?? []);
let scriptsObfuscated = 0;

const packagedHtml = source.replace(/<script(\s[^>]*)?>([\s\S]*?)<\/script>/gi, (tag, attributes = '', code) => {
  if (!code.trim()) return tag;
  const ast = parse(code, { ecmaVersion: 'latest', sourceType: 'script' });
  const globalNames = new Set();
  for (const node of ast.body) {
    if (node.type === 'FunctionDeclaration' && node.id) globalNames.add(node.id.name);
    if (node.type === 'VariableDeclaration') {
      for (const declaration of node.declarations) {
        if (declaration.id.type === 'Identifier') globalNames.add(declaration.id.name);
      }
    }
  }
  const publicNames = [...globalNames].filter((name) => handlerTokens.has(name));
  const descriptors = publicNames.map((name) => `${JSON.stringify(name)}:{configurable:true,get:()=>${name},set:value=>${name}=value}`).join(',');
  const wrappedCode = `(()=>{${code}\nObject.defineProperties(window,{${descriptors}});})();`;
  const protectedCode = JavaScriptObfuscator.obfuscate(wrappedCode, {
    compact: true,
    target: 'browser',
    identifierNamesGenerator: 'hexadecimal',
    renameGlobals: false,
    stringArray: true,
    stringArrayEncoding: ['base64'],
    stringArrayThreshold: 0.65,
    splitStrings: true,
    splitStringsChunkLength: 10,
    controlFlowFlattening: false,
    deadCodeInjection: false,
    debugProtection: false,
    selfDefending: false,
    transformObjectKeys: false,
    unicodeEscapeSequence: false,
    sourceMap: false,
  }).getObfuscatedCode();
  scriptsObfuscated += 1;
  return `<script${attributes}>${protectedCode}</script>`;
});

if (scriptsObfuscated === 0) {
  throw new Error('No inline JavaScript was found; refusing to package an unobfuscated APK asset.');
}

await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, packagedHtml, 'utf8');
console.log(`Prepared ${outputPath} with ${scriptsObfuscated} obfuscated inline script(s).`);
