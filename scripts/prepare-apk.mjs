import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const sourcePath = resolve(root, 'www/index.html');
const outputPath = resolve(root, 'dist/index.html');

const source = await readFile(sourcePath, 'utf8');

// نسخه‌ی HTML جدید بسیار بزرگ است (بیش از ۱ مگابایت) و obfuscation
// باعث خراب شدن آرایه LESSONS و سایر داده‌های آموزشی می‌شود.
// بنابراین فایل را بدون تغییر کپی می‌کنیم.
await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, source, 'utf8');

console.log('Prepared ' + outputPath + ' (copy, no obfuscation).');
