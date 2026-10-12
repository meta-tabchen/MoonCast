#!/usr/bin/env node
/* GPL-3.0-only. Browser-only integration test; this does not validate Android capture. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const fsp = fs.promises;
const http = require('node:http');
const path = require('node:path');
const crypto = require('node:crypto');
const os = require('node:os');
const {execFileSync, spawnSync} = require('node:child_process');

const repo = path.resolve(__dirname, '..');
const assets = path.join(repo, 'app/src/main/assets/browser');
const output = path.resolve(process.env.BROWSER_EVIDENCE_DIR || path.join(repo, '../browser-evidence'));
const chrome = process.env.CHROME_PATH || ['/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'].find(file => fs.existsSync(file));
const puppeteerModule = process.env.PUPPETEER_MODULE || 'puppeteer-core';
let puppeteer;
try { puppeteer = require(puppeteerModule); }
catch (_) { throw new Error('puppeteer-core is required. Set PUPPETEER_MODULE to an existing installation, e.g. /path/to/node_modules/puppeteer-core. See tests/browser-receiver-README.md.'); }
if (!chrome) throw new Error('Chrome was not found. Set CHROME_PATH to the installed browser executable.');
const timeout = 25000;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const checks = [];
const events = [];
const errors = [];
const sessionRequests = [];
const sessions = new Map();
const localAddresses = Object.values(os.networkInterfaces()).flat().map(item => item.address);
const bindAddress = process.env.BROWSER_BIND_ADDRESS || '127.0.0.1';
const nativeCandidateAddress = process.env.BROWSER_NATIVE_CANDIDATE_ADDRESS;
if (!localAddresses.includes(bindAddress)) throw new Error('BROWSER_BIND_ADDRESS must be an existing local interface address.');
if (nativeCandidateAddress && !localAddresses.includes(nativeCandidateAddress)) throw new Error('BROWSER_NATIVE_CANDIDATE_ADDRESS must be an existing local interface address.');
let browser, sender, receiver, server;
let nextId = 1;
let failHeartbeats = false;
let failSessions = false;
let dropNextAnswer = false;
let sharedAudio = true;
let baseUrl;

async function check(name, action) {
  const started = Date.now();
  await action();
  checks.push({name, passed: true, durationMs: Date.now() - started});
  process.stdout.write(`PASS ${name}\n`);
}
function send(res, status, value, contentType = 'application/json') {
  res.writeHead(status, {'Content-Type': contentType, 'Cache-Control': 'no-store'});
  res.end(typeof value === 'string' || Buffer.isBuffer(value) ? value : JSON.stringify(value));
}
async function readBody(req) {
  let body = '';
  for await (const chunk of req) {
    body += chunk;
    if (body.length > 256 * 1024) throw new Error('Oversize signaling body');
  }
  return JSON.parse(body);
}
async function closeSession(id, reason) {
  const session = sessions.get(id);
  if (!session || session.closed) return;
  session.closed = true;
  events.push({event: 'close', id, reason});
  await sender.evaluate(id => window.fixture.stop(id), id);
}
async function handle(req, res) {
  const url = new URL(req.url, baseUrl);
  const route = url.pathname;
  if (route === '/fixture.html') return send(res, 200, await fsp.readFile(path.join(__dirname, 'browser_sender_fixture.html')), 'text/html; charset=utf-8');
  if (route === '/favicon.ico') return send(res, 204, '');
  if (!route.startsWith('/browser/test-token/')) return send(res, 404, {error: 'Not found'});
  const relative = route.slice('/browser/test-token/'.length);
  if (req.method === 'POST' && relative === 'session') {
    const body = await readBody(req);
    assert.match(body.viewerId, /^[a-f0-9]{32}$/, 'Receiver supplies a random viewer capability');
    sessionRequests.push({previousSessionId: body.previousSessionId || null, viewerHash: crypto.createHash('sha256').update(body.viewerId).digest('hex'), time: Date.now()});
    if (failSessions) return send(res, 503, {error: 'Synthetic sender temporarily unavailable'});
    if (body.previousSessionId && sessions.get(body.previousSessionId)?.viewerId === body.viewerId) await closeSession(body.previousSessionId, 'takeover');
    for (const [existingId, session] of sessions) if (!session.closed && session.viewerId === body.viewerId) await closeSession(existingId, 'same viewer recovery');
    // Match the product contract: only one browser owns the sender at a time.
    if ([...sessions.values()].some(s => !s.closed)) return send(res, 409, {error: 'Synthetic receiver busy'});
    const id = `synthetic-${nextId++}`;
    const answer = await sender.evaluate(value => window.fixture.answer(value), {id, sdp: body.sdp});
    const candidateClass = address => address.endsWith('.local') ? 'mDNS' : address.startsWith('127.') ? 'loopback' : address.includes(':') ? 'IPv6' : 'IPv4';
    const candidateSummary = sdp => sdp.split('\r\n').filter(line => line.startsWith('a=candidate:')).map(line => { const parts = line.split(' '); return {protocol:parts[2],addressClass:candidateClass(parts[4]),type:parts[7]}; });
    events.push({event:'candidates',id,offer:candidateSummary(body.sdp),answer:candidateSummary(answer.sdp)});
    if (nativeCandidateAddress) {
      let replaced = 0;
      answer.sdp = answer.sdp.split('\r\n').map(line => {
        if (!line.startsWith('a=candidate:')) return line;
        const parts = line.split(' ');
        if (parts[4].endsWith('.local')) { parts[4] = nativeCandidateAddress; replaced++; }
        return parts.join(' ');
      }).join('\r\n');
      events.push({event:'native-style numeric answer fixture',id,replacedCandidateAddresses:replaced});
    }
    sessions.set(id, {closed: false, viewerId: body.viewerId});
    events.push({event: 'answer', id});
    if (dropNextAnswer) {
      dropNextAnswer = false;
      events.push({event:'injected lost answer',id});
      return res.destroy();
    }
    return send(res, 200, answer);
  }
  if (req.method === 'GET' && relative.startsWith('status/')) {
    const id = relative.slice(7);
    if (failHeartbeats) return send(res, 503, {error: 'Injected heartbeat failure'});
    if (!sessions.has(id) || sessions.get(id).closed) return send(res, 404, {error: 'Session ended'});
    const state = await sender.evaluate(id => window.fixture.state(id), id);
    return send(res, 200, {state: state === 'connected' ? 'connected' : state, audio: sharedAudio});
  }
  if (req.method === 'DELETE' && relative.startsWith('session/')) {
    await closeSession(relative.slice(8), 'delete');
    return send(res, 200, {ok: true});
  }
  const name = relative || 'index.html';
  if (!['index.html', 'receiver.css', 'receiver.js'].includes(name) || req.method !== 'GET') return send(res, 404, {error: 'Not found'});
  const type = name.endsWith('.js') ? 'text/javascript' : name.endsWith('.css') ? 'text/css' : 'text/html';
  res.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; media-src 'self' blob:; img-src 'self' data:; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
  res.setHeader('Permissions-Policy', 'camera=(),microphone=(),geolocation=()');
  return send(res, 200, await fsp.readFile(path.join(assets, name)), `${type}; charset=utf-8`);
}
async function waitState(state, wait = timeout) {
  await receiver.waitForFunction(expected => (document.querySelector('#app')?.dataset.state || document.body.dataset.state) === expected, {timeout: wait}, state);
}
async function mediaSnapshot() {
  return receiver.evaluate(async () => {
    const video = document.querySelector('video');
    const pc = window.__testPeerConnections.findLast(pc => pc.connectionState === 'connected');
    const reports = pc ? [...(await pc.getStats()).values()] : [];
    const canvas = document.createElement('canvas');
    canvas.width = 32; canvas.height = 18;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, 32, 18);
    const pixels = ctx.getImageData(0, 0, 32, 18).data;
    const rgb = Array.from(pixels).filter((_, index) => index % 4 !== 3);
    return {
      currentTime: video.currentTime, paused: video.paused, muted: video.muted,
      width: video.videoWidth, height: video.videoHeight,
      frames: video.getVideoPlaybackQuality().totalVideoFrames,
      pixelRange: Math.max(...rgb) - Math.min(...rgb),
      tracks: video.srcObject?.getTracks().map(track => ({kind: track.kind, state: track.readyState})),
      inbound: reports.filter(r => r.type === 'inbound-rtp').map(r => ({kind: r.kind, bytesReceived: r.bytesReceived, packetsReceived: r.packetsReceived, framesDecoded: r.framesDecoded, totalAudioEnergy: r.totalAudioEnergy, totalSamplesReceived: r.totalSamplesReceived})),
      selectedPairs: reports.filter(r => r.type === 'candidate-pair' && r.state === 'succeeded').map(r => ({state: r.state, nominated: r.nominated, bytesReceived: r.bytesReceived}))
    };
  });
}
async function setLanguage(code) {
  const type = await receiver.$eval('#language', el => el.tagName);
  if (type === 'SELECT') {
    const value = await receiver.$eval('#language', (el, code) => [...el.options].find(option => option.value.startsWith(code))?.value, code);
    assert.ok(value, `Language option ${code}`);
    await receiver.select('#language', value);
  } else {
    const current = await receiver.$eval('html', el => el.lang);
    if (!current.startsWith(code)) await receiver.click('#language');
  }
  await receiver.waitForFunction(code => document.documentElement.lang.startsWith(code), {}, code);
}
async function recordDemo() {
  const frameDir = path.join(output, 'demo-frames');
  await fsp.mkdir(frameDir, {recursive: true});
  const client = await receiver.createCDPSession();
  let frame = 0;
  let lastTime = 0;
  const writes = [];
  const handler = event => {
    client.send('Page.screencastFrameAck', {sessionId: event.sessionId}).catch(() => {});
    const time = event.metadata.timestamp;
    if (time - lastTime < 0.095) return;
    lastTime = time;
    writes.push(fsp.writeFile(path.join(frameDir, `${String(frame++).padStart(5, '0')}.jpg`), Buffer.from(event.data, 'base64')));
  };
  client.on('Page.screencastFrame', handler);
  await client.send('Page.startScreencast', {format: 'jpeg', quality: 82, maxWidth: 1280, maxHeight: 850, everyNthFrame: 1});
  await sleep(1800);
  await receiver.click('[data-fit="fill"]');
  await sleep(1800);
  await receiver.click('[data-fit="fit"]');
  await setLanguage('zh');
  await sleep(1800);
  await setLanguage('en');
  await sleep(1000);
  await client.send('Page.stopScreencast');
  client.off('Page.screencastFrame', handler);
  await Promise.all(writes);
  await client.detach();
  const version = spawnSync('ffmpeg', ['-version'], {encoding: 'utf8'});
  if (version.status === 0 && frame > 10) {
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-framerate', '10', '-i', path.join(frameDir, '%05d.jpg'), '-vf', 'scale=960:-2:flags=lanczos', '-c:v', 'libvpx-vp9', '-crf', '34', '-b:v', '0', path.join(output, 'browser-synthetic-demo.webm')]);
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-framerate', '10', '-i', path.join(frameDir, '%05d.jpg'), '-filter_complex', '[0:v]scale=720:-2:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse', path.join(output, 'browser-synthetic-demo.gif')]);
    await fsp.rm(frameDir, {recursive: true});
    return {frames: frame, webm: 'browser-synthetic-demo.webm', gif: 'browser-synthetic-demo.gif', audio: false, scope: 'Actual receiver webpage, synthetic WebRTC canvas video; screencast has no audio track'};
  }
  return {frames: frame, frameDirectory: 'demo-frames', scope: 'Actual receiver webpage with synthetic WebRTC canvas video', videoEncodingUnavailable: true};
}

(async () => {
  const report = {startedAt: new Date().toISOString(), scope: 'Browser-only synthetic WebRTC integration. Not Android-to-browser end-to-end evidence.', checks, events, errors};
  await fsp.mkdir(output, {recursive: true});
  try {
    for (const name of ['index.html', 'receiver.js', 'receiver.css']) assert.ok(fs.existsSync(path.join(assets, name)), `Missing receiver asset ${name}`);
    report.assets = Object.fromEntries(['index.html', 'receiver.js', 'receiver.css'].map(name => [name, crypto.createHash('sha256').update(fs.readFileSync(path.join(assets, name))).digest('hex')]));
    report.sourceCommit = execFileSync('git', ['rev-parse', 'HEAD'], {cwd: repo, encoding: 'utf8'}).trim();
    report.sourceTreeDirty = execFileSync('git', ['status', '--porcelain'], {cwd: repo, encoding: 'utf8'}).trim().length > 0;
    report.sourceCommitMeaning = 'Working-tree base HEAD; receiver asset SHA256 hashes identify the tested code, including uncommitted changes.';
    report.nodeVersion = process.version;
    report.puppeteerVersion = require(puppeteerModule + '/package.json').version;
    report.chromeArguments = ['--disable-background-timer-throttling', '--disable-backgrounding-occluded-windows', '--disable-renderer-backgrounding'];
    report.nativeStyleAnswer = !!nativeCandidateAddress;
    browser = await puppeteer.launch({executablePath: chrome, headless: true, args: report.chromeArguments, defaultViewport: {width: 1440, height: 1000}});
    report.chromeVersion = await browser.version();
    server = http.createServer((req, res) => handle(req, res).catch(error => { errors.push({server: error.message}); if (!res.headersSent) send(res, 500, {error: error.message}); else res.end(); }));
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, bindAddress, resolve); });
    baseUrl = `http://${bindAddress}:${server.address().port}`;
    report.origin = bindAddress === '127.0.0.1' ? 'http://127.0.0.1:<ephemeral-port>' : 'http://<own-private-lan-ip>:<ephemeral-port>';
    report.originScope = bindAddress === '127.0.0.1' ? 'loopback HTTP' : 'own-interface LAN HTTP';
    sender = await browser.newPage();
    await sender.goto(`${baseUrl}/fixture.html`);
    await sender.click('#start');
    await sender.waitForFunction(() => document.querySelector('#start').textContent === 'Synthetic audio ready');
    receiver = await browser.newPage();
    receiver.on('pageerror', error => errors.push({page: error.message}));
    await receiver.evaluateOnNewDocument(() => {
      window.__testCspViolations = [];
      addEventListener('securitypolicyviolation', event => window.__testCspViolations.push({directive:event.effectiveDirective,blockedUri:event.blockedURI}));
      const NativePeerConnection = window.RTCPeerConnection;
      window.__testPeerConnections = [];
      window.RTCPeerConnection = class extends NativePeerConnection {
        constructor(...args) { super(...args); window.__testPeerConnections.push(this); }
      };
    });
    await receiver.goto(`${baseUrl}/browser/test-token/`, {waitUntil: 'networkidle0'});
    report.browserPolicy = await receiver.evaluate(() => ({isSecureContext,rtcPeerConnectionAvailable:typeof RTCPeerConnection==='function',getUserMediaAvailable:!!navigator.mediaDevices?.getUserMedia}));
    dropNextAnswer = true;
    await check('Manual connect establishes real ICE/DTLS/SRTP video and audio', async () => {
      await receiver.click('#connect');
      await waitState('live');
      await receiver.waitForFunction(() => document.querySelector('video').getVideoPlaybackQuality().totalVideoFrames >= 15, {timeout});
      const first = await mediaSnapshot();
      await sleep(500);
      const second = await mediaSnapshot();
      assert.ok(second.width >= 320 && second.height >= 180, 'Decoded synthetic video has usable dimensions');
      assert.ok(Math.abs(second.width / second.height - 16 / 9) < 0.01, 'Synthetic video keeps its source aspect ratio');
      assert.ok(second.currentTime > first.currentTime);
      assert.ok(second.frames > first.frames && second.pixelRange > 80);
      assert.ok(second.inbound.some(r => r.kind === 'video' && r.bytesReceived > 0 && r.framesDecoded > 0));
      assert.ok(second.inbound.some(r => r.kind === 'audio' && r.bytesReceived > 0));
      assert.ok(second.selectedPairs.some(r => r.nominated));
      report.media = second;
    });
    await check('Lost first answer recovers with same viewer and no previous session id', async () => {
      assert.ok(events.some(event => event.event === 'injected lost answer'));
      assert.ok(events.some(event => event.reason === 'same viewer recovery'));
      assert.equal(sessionRequests[0].previousSessionId, null);
      assert.equal(sessionRequests[1].previousSessionId, null);
      assert.equal(sessionRequests[0].viewerHash, sessionRequests[1].viewerHash);
    });
    await check('A different viewer receives terminal busy without replacing owner', async () => {
      const activeId = [...sessions.entries()].find(([, session]) => !session.closed)[0];
      const other = await browser.newPage();
      try {
        await other.goto(`${baseUrl}/browser/test-token/`);
        await other.click('#connect');
        await other.waitForFunction(() => document.body.dataset.state === 'error', {timeout});
        assert.match(await other.$eval('#hint', element => element.textContent), /Another browser|另一个浏览器/);
        assert.equal(sessions.get(activeId).closed, false);
      } finally { await other.close(); }
    });
    await check('Explicit sound control unmutes incoming audio', async () => {
      if (await receiver.$eval('video', video => video.muted)) await receiver.click('#sound');
      assert.equal(await receiver.$eval('video', video => video.muted), false);
      await receiver.waitForFunction(async () => { const pc = window.__testPeerConnections.findLast(pc => pc.connectionState === 'connected'); return pc && [...(await pc.getStats()).values()].some(r => r.type === 'inbound-rtp' && r.kind === 'audio' && r.totalAudioEnergy > 0); }, {timeout: 10000});
      report.mediaAfterUnmute = await mediaSnapshot();
      report.audioOutputLimitation = 'Incoming nonzero synthetic RTP audio energy and an unmuted playing element verified; physical speaker audibility not measured.';
    });
    await check('Sender audio downgrade mutes and disables sound until capability returns', async () => {
      sharedAudio = false;
      await receiver.waitForFunction(() => document.querySelector('#sound').disabled && document.querySelector('video').muted, {timeout: 8000});
      assert.match(await receiver.$eval('#sound-label', element => element.textContent), /No shared audio|未分享声音/);
      assert.match(await receiver.$eval('#hint', element => element.textContent), /video only|仅分享画面/);
      sharedAudio = true;
      await receiver.waitForFunction(() => !document.querySelector('#sound').disabled, {timeout: 8000});
      assert.equal(await receiver.$eval('video', video => video.muted), true);
      await receiver.click('#sound');
    });
    await check('Fit/Fill, zoom, drag pan and reset controls work', async () => {
      await receiver.click('[data-fit="fill"]');
      assert.equal(await receiver.$eval('[data-fit="fill"]', el => el.getAttribute('aria-pressed')), 'true');
      await receiver.click('[data-fit="fit"]');
      assert.equal(await receiver.$eval('[data-fit="fit"]', el => el.getAttribute('aria-pressed')), 'true');
      await receiver.$eval('#zoom', el => { el.value = Math.min(Number(el.max), Math.max(Number(el.min), 1.6)); el.dispatchEvent(new Event('input', {bubbles: true})); });
      const before = await receiver.$eval('video', el => getComputedStyle(el).transform);
      const box = await receiver.$eval('#stage', el => { const r = el.getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height}; });
      await receiver.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
      await receiver.mouse.down(); await receiver.mouse.move(box.x + box.width / 2 + 55, box.y + box.height / 2 + 30, {steps: 8}); await receiver.mouse.up();
      const after = await receiver.$eval('video', el => getComputedStyle(el).transform);
      assert.notEqual(after, before, 'Pan changes video transform at enlarged scale');
      await receiver.click('#reset');
      const reset = Number(await receiver.$eval('#zoom', el => el.value));
      assert.ok(reset === 1 || reset === 100, `Reset zoom has expected value, got ${reset}`);
    });
    await check('Fullscreen enters and exits after user gesture', async () => {
      await receiver.click('#fullscreen');
      await receiver.waitForFunction(() => !!document.fullscreenElement);
      await receiver.evaluate(() => document.exitFullscreen());
    });
    await check('English/Chinese UI and 375/768/1440 layouts', async () => {
      await setLanguage('zh');
      const zh = await receiver.$eval('#status-text', el => el.textContent);
      await setLanguage('en');
      assert.notEqual(await receiver.$eval('#status-text', el => el.textContent), zh);
      for (const width of [375, 768, 1440]) {
        await receiver.setViewport({width, height: width === 375 ? 900 : 1000});
        await sleep(250);
        assert.ok(await receiver.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `No horizontal overflow at ${width}`);
        await receiver.screenshot({path: path.join(output, `receiver-${width}.png`), fullPage: true});
      }
    });
    await check('Record actual receiver webpage with synthetic source label', async () => { report.demo = await recordDemo(); });
    await check('Reload reconnects using previous session takeover', async () => {
      const oldId = [...sessions.entries()].find(([, s]) => !s.closed)[0];
      const count = sessionRequests.length;
      await receiver.reload({waitUntil: 'networkidle0'});
      await receiver.click('#connect');
      await waitState('live');
      assert.equal(sessionRequests[count].previousSessionId, oldId);
      assert.ok(sessions.get(oldId).closed);
    });
    await check('Heartbeat/sender loss recovers through bounded reconnect', async () => {
      const oldId = [...sessions.entries()].find(([, s]) => !s.closed)[0];
      await closeSession(oldId, 'injected sender loss');
      failHeartbeats = true;
      await receiver.waitForFunction(() => ['reconnecting', 'connecting', 'error'].includes((document.querySelector('#app')?.dataset.state || document.body.dataset.state)), {timeout: 30000});
      failHeartbeats = false;
      await waitState('live', 40000);
      assert.ok(sessionRequests.at(-1).previousSessionId === oldId || events.some(e => e.reason === 'delete' && e.id === oldId));
    });
    await check('Persistent signaling failure stops retrying and explicit stop releases session', async () => {
      failHeartbeats = true;
      failSessions = true;
      const oldId = [...sessions.entries()].find(([, s]) => !s.closed)[0];
      await closeSession(oldId, 'injected persistent outage');
      await waitState('error', 90000);
      const attempts = sessionRequests.length;
      await sleep(3500);
      assert.equal(sessionRequests.length, attempts, 'Retries stop at finite limit');
      failHeartbeats = false; failSessions = false;
      await receiver.click('#connect'); await waitState('live');
      await receiver.click('#disconnect'); await waitState('idle');
      await receiver.waitForFunction(() => !document.querySelector('video').srcObject);
      await sleep(300);
      assert.ok([...sessions.values()].every(session => session.closed));
      assert.equal(await sender.evaluate(() => window.fixture.active().length), 0);
    });
    assert.deepEqual(errors, [], 'No unhandled page/server errors');
    report.cspViolations = await receiver.evaluate(() => window.__testCspViolations);
    assert.deepEqual(report.cspViolations, [], 'No receiver CSP violations');
    report.passed = true;
  } catch (error) {
    report.passed = false;
    report.failure = {message: error.message, stack: error.stack};
    if (receiver) {
      await receiver.screenshot({path: path.join(output, 'failure.png'), fullPage: true}).catch(() => {});
      report.failure.state = await receiver.evaluate(() => ({state: (document.querySelector('#app')?.dataset.state || document.body.dataset.state), text: document.body.innerText})).catch(() => null);
      report.failure.peers = await receiver.evaluate(async () => Promise.all(window.__testPeerConnections.map(async pc => ({connection: pc.connectionState, ice: pc.iceConnectionState, signaling: pc.signalingState, tracks: pc.getReceivers().map(r => ({kind:r.track.kind, state:r.track.readyState, muted:r.track.muted})), stats:[...(await pc.getStats()).values()].filter(r=>['candidate-pair','local-candidate','remote-candidate','inbound-rtp'].includes(r.type)).map(r=>({type:r.type,state:r.state,kind:r.kind,candidateType:r.candidateType,addressClass:!r.address?'absent':r.address.endsWith('.local')?'mDNS':r.address.startsWith('127.')?'loopback':r.address.includes(':')?'IPv6':'IPv4',requestsSent:r.requestsSent,responsesReceived:r.responsesReceived,protocol:r.protocol,bytesReceived:r.bytesReceived,bytesSent:r.bytesSent,packetsReceived:r.packetsReceived,framesDecoded:r.framesDecoded}))})))).catch(() => null);
      report.failure.sender = await sender.evaluate(() => window.fixture.diagnostics()).catch(() => null);
    }
    process.stderr.write(`${error.stack}\n`);
    process.exitCode = 1;
  } finally {
    report.finishedAt = new Date().toISOString();
    report.sessionRequests = sessionRequests;
    await fsp.writeFile(path.join(output, 'browser-smoke-report.json'), JSON.stringify(report, null, 2));
    if (browser) await browser.close();
    if (server) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
    process.stdout.write(`Evidence: ${output}\n`);
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
