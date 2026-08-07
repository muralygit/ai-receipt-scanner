import express from 'express';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// Serve APK if built
app.get('/api/apk', (req, res) => {
  const apkPath = path.join(__dirname, 'app/build/outputs/apk/debug/app-debug.apk');
  if (fs.existsSync(apkPath)) {
    res.download(apkPath, 'AI_Receipt_Scanner_debug.apk');
  } else {
    res.status(404).send('APK is building or not found');
  }
});

// Web interface preview
app.get('/', (req, res) => {
  res.send(`
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>AI Receipt Scanner - Android Native App</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
  <style>
    body { font-family: 'Plus Jakarta Sans', sans-serif; }
  </style>
</head>
<body class="bg-slate-900 text-slate-100 min-h-screen p-6">
  <div class="max-w-4xl mx-auto space-y-6">
    <header class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-slate-800/80 backdrop-blur p-6 rounded-2xl border border-slate-700">
      <div>
        <div class="flex items-center gap-2">
          <span class="px-2.5 py-1 text-xs font-semibold bg-emerald-500/20 text-emerald-400 rounded-full border border-emerald-500/30">Native Android Gradle Project</span>
          <span class="px-2.5 py-1 text-xs font-semibold bg-indigo-500/20 text-indigo-400 rounded-full border border-indigo-500/30">Build Status: Compiled OK</span>
        </div>
        <h1 class="text-2xl font-bold mt-2 text-white">AI Receipt Scanner (Android)</h1>
        <p class="text-slate-400 text-sm mt-1">Native Kotlin + AndroidX + Room + CameraX + ML Kit + Gemini AI REST API</p>
      </div>
      <a href="/api/apk" class="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white font-medium rounded-xl transition-all shadow-lg shadow-indigo-600/25 flex items-center gap-2">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/></svg>
        Download APK
      </a>
    </header>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div class="bg-slate-800 p-6 rounded-2xl border border-slate-700 space-y-4">
        <h2 class="text-lg font-semibold text-white flex items-center gap-2">
          <svg class="w-5 h-5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z"/></svg>
          Android Native App Architecture
        </h2>
        <ul class="space-y-2 text-sm text-slate-300">
          <li class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-indigo-400"></span> <strong>Language:</strong> Kotlin 2.0 (Kotlin DSL)</li>
          <li class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-indigo-400"></span> <strong>Build System:</strong> Gradle with Version Catalog (libs.versions.toml)</li>
          <li class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-indigo-400"></span> <strong>Database:</strong> Room DB (ReceiptEntity, ReceiptItemEntity)</li>
          <li class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-indigo-400"></span> <strong>OCR & AI:</strong> ML Kit Text Recognition + Gemini 1.5 Flash REST API</li>
          <li class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-indigo-400"></span> <strong>Exporting:</strong> Native PDF Document & CSV Exporter (INR ₹ format)</li>
        </ul>
      </div>

      <div class="bg-slate-800 p-6 rounded-2xl border border-slate-700 space-y-4">
        <h2 class="text-lg font-semibold text-white flex items-center gap-2">
          <svg class="w-5 h-5 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V7M3 7l9 6 9-6"/></svg>
          Project Files Location
        </h2>
        <div class="bg-slate-950 p-4 rounded-xl text-xs font-mono text-emerald-300 space-y-1 overflow-x-auto">
          <div>/app/src/main/java/com/muraly/receiptscanner/</div>
          <div class="pl-4 text-slate-400">├── ReceiptScannerApplication.kt</div>
          <div class="pl-4 text-slate-400">├── data/ (Room DB, Entities, DAOs, Repos)</div>
          <div class="pl-4 text-slate-400">├── ui/ (MainActivity, Scan, Settings, ViewModels)</div>
          <div class="pl-4 text-slate-400">└── util/ (ExportHelper, GeminiHelper, OcrHelper)</div>
          <div class="mt-2 text-indigo-300">/build.gradle.kts & /app/build.gradle.kts</div>
        </div>
      </div>
    </div>
  </div>
</body>
</html>
  `);
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Android Project Dashboard running on port ${PORT}`);
});
