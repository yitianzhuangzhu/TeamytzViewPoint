// Download a portable Temurin JDK 8 for the 1.12.2 Forge toolchain.
// Runs under the bundled Node runtime because the sandboxed shell cannot use Schannel.
// Usage: node download-jdk8.js <outZip>
const fs = require('fs');
const path = require('path');

const out = process.argv[2] || path.join(__dirname, 'jdk8.zip');
const API = 'https://api.adoptium.net/v3/binary/latest/8/ga/windows/x64/jdk/hotspot/normal/eclipse';
const TUNA_DIR = 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/8/jdk/x64/windows/';

async function pickTuna() {
  try {
    const res = await fetch(TUNA_DIR, { redirect: 'follow' });
    if (!res.ok) return null;
    const html = await res.text();
    const names = [...html.matchAll(/href="([^"]*OpenJDK8U-jdk_x64_windows_hotspot_[^"]*\.zip)"/g)].map(m => m[1]);
    if (!names.length) return null;
    names.sort();
    return new URL(names[names.length - 1], TUNA_DIR).toString();
  } catch (e) {
    console.log('tuna listing failed: ' + e.message);
    return null;
  }
}

async function download(url, label) {
  console.log('[' + label + '] GET ' + url);
  const started = Date.now();
  const res = await fetch(url, { redirect: 'follow' });
  if (!res.ok) throw new Error('HTTP ' + res.status + ' ' + res.statusText);
  const total = Number(res.headers.get('content-length') || 0);
  const file = fs.createWriteStream(out);
  let got = 0, lastLog = 0;
  for await (const chunk of res.body) {
    got += chunk.length;
    if (!file.write(chunk)) await new Promise(r => file.once('drain', r));
    const now = Date.now();
    if (now - lastLog > 5000) {
      lastLog = now;
      const pct = total ? ((got / total) * 100).toFixed(1) + '%' : '?';
      console.log('  ' + (got / 1048576).toFixed(1) + ' MB / ' + (total ? (total / 1048576).toFixed(1) : '?') + ' MB (' + pct + ')');
    }
  }
  await new Promise(r => file.end(r));
  console.log('[' + label + '] done: ' + (got / 1048576).toFixed(1) + ' MB in ' + ((Date.now() - started) / 1000).toFixed(0) + 's');
  if (got < 50 * 1048576) throw new Error('suspiciously small download (' + got + ' bytes)');
  return got;
}

(async () => {
  const candidates = [];
  const tuna = await pickTuna();
  if (tuna) candidates.push([tuna, 'tuna-mirror']);
  candidates.push([API, 'adoptium-api']);
  for (const [url, label] of candidates) {
    try {
      await download(url, label);
      console.log('OK ' + out);
      return;
    } catch (e) {
      console.log('[' + label + '] FAILED: ' + e.message);
      try { fs.unlinkSync(out); } catch (_) {}
    }
  }
  console.error('ALL SOURCES FAILED');
  process.exit(1);
})();
