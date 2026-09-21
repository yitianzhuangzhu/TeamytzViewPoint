// Minimal HTTPS downloader used by the agent tooling (Schannel is unavailable in the sandboxed shell,
// Node carries its own TLS stack). Usage: node fetch.js <url> <outFile>
const fs = require('fs');
const url = process.argv[2];
const out = process.argv[3];
if (!url || !out) { console.error('usage: node fetch.js <url> <outFile>'); process.exit(2); }

(async () => {
  try {
    const res = await fetch(url, { redirect: 'follow' });
    if (!res.ok) { console.error('HTTP ' + res.status + ' ' + res.statusText + ' for ' + url); process.exit(3); }
    const buf = Buffer.from(await res.arrayBuffer());
    fs.writeFileSync(out, buf);
    console.log('saved ' + buf.length + ' bytes -> ' + out);
  } catch (e) {
    console.error('ERR ' + (e && e.message ? e.message : e));
    process.exit(1);
  }
})();
