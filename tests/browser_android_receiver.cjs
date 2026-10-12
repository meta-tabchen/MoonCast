#!/usr/bin/env node
/* GPL-3.0-only. Real Android sender -> owned Chrome receiver verification.
 * Usage: PUPPETEER_MODULE=/path/to/puppeteer-core node tests/browser_android_receiver.cjs
 *   --url-file /tmp/private-mooncast-url --output /tmp/mooncast-android-evidence
 *   --headful --record --await-stop --control-file /tmp/mooncast-browser-control
 * No ADB, generated sender, SDP rewriting, browser permission override, or user profile.
 * Control file: after a private preview, write "record" only when the owner confirms
 * synthetic-only source content; write "stop" only after the Android owner stopped
 * sharing. Evidence remains private until the owner confirms token retirement.
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const fsp = fs.promises;
const path = require('node:path');
const crypto = require('node:crypto');
const net = require('node:net');
const {execFileSync, spawnSync} = require('node:child_process');

const args = process.argv.slice(2);
const value = name => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : undefined; };
const has = name => args.includes(name);
if (has('--help')) {
  console.log('Usage: browser_android_receiver.cjs --url-file FILE | --url URL [--output DIR] [--headful] [--record] [--await-stop] [--control-file FILE] [--sender-build-label LABEL] [--video-only]');
  process.exit(0);
}
const urlText = (value('--url-file') ? fs.readFileSync(value('--url-file'), 'utf8') : value('--url') || '').trim();
if (!urlText) throw new Error('Supply --url-file with the current, private Android MoonCast browser URL.');
let target;
try { target = new URL(urlText); } catch (_) { throw new Error('The receiver URL is invalid; its contents have been omitted.'); }
const privateV4 = host => net.isIP(host) === 4 && (/^10\./.test(host) || /^192\.168\./.test(host) || /^172\.(1[6-9]|2\d|3[01])\./.test(host));
if (target.protocol !== 'http:' || !privateV4(target.hostname) || target.username || target.password) throw new Error('This driver accepts only a private-LAN IPv4 HTTP receiver URL.');
const output = path.resolve(value('--output') || path.join(__dirname, '../../browser-evidence/android-real'));
const controlFile = value('--control-file');
if ((has('--await-stop') || has('--record')) && !controlFile) throw new Error('--await-stop and --record require --control-file for owner confirmation.');
if (fs.existsSync(path.join(output,'android-receiver-report.json'))) throw new Error('The evidence directory already has a report. Choose a fresh --output directory for this run.');
const puppeteerModule = process.env.PUPPETEER_MODULE || 'puppeteer-core';
let puppeteer;
try { puppeteer = require(puppeteerModule); }
catch (_) { throw new Error('Set PUPPETEER_MODULE to an existing puppeteer-core installation.'); }
const chrome = process.env.CHROME_PATH || ['/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', '/usr/bin/google-chrome', '/usr/bin/chromium'].find(file => fs.existsSync(file));
if (!chrome) throw new Error('Set CHROME_PATH to the installed Chrome executable.');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const safe = value => String(value).replaceAll(urlText, '<private receiver URL>').replace(/https?:\/\/[^\s"'<>]+/g, '<redacted URL>');
const report = {
  startedAt: new Date().toISOString(),
  scope: 'Verify real Android MoonCast screen/playback capture -> Mac Chrome over LAN WebRTC. Intended source is a synthetic test pattern/tone; owner confirmation of the private preview is required before recording.',
  senderBuildLabel: value('--sender-build-label') || 'not supplied',
  origin: 'http://<Android-private-LAN-IP>:<port>/<revocable-session-path>/',
  syntheticBrowserSender: false, sdpRewritten: false,
  securityOverrides: false, userBrowserProfile: false,
  publishable: false, tokenRevoked: false, oldUrlNoLongerServed: false,
  privacyNote: 'Keep evidence private until the Android owner confirms synthetic-only source content and stops/revokes this sharing URL.',
  checks: [], samples: [], events: [], errors: [], servedAssets: {},
  recordingStatus: has('--record') ? 'requested-not-started' : 'not-requested',
  expectedAudio: !has('--video-only')
};
let browser, page;
const artifactHashes = {};
const generatedArtifacts = new Set();
const responseTasks = [];

async function saveReport() {
  report.updatedAt = new Date().toISOString();
  await fsp.writeFile(path.join(output, 'android-receiver-report.json'), JSON.stringify(report, null, 2));
}
async function check(name, action) {
  try {
    const detail = await action();
    report.checks.push({name, passed: true, ...(detail ? {detail} : {})});
    console.log(`PASS ${name}`);
  } catch (error) {
    report.checks.push({name, passed: false, message: safe(error.message)});
    console.log(`FAIL ${name}: ${safe(error.message)}`);
  }
  await saveReport();
}
async function snapshot() {
  return page.evaluate(async () => {
    const video = document.querySelector('#screen');
    const peers = window.__androidTestPeers || [];
    const pc = [...peers].reverse().find(item => item.connectionState === 'connected') || peers.at(-1);
    const stats = pc ? [...(await pc.getStats()).values()] : [];
    const codecs = Object.fromEntries(stats.filter(item => item.type === 'codec').map(item => [item.id, {mimeType:item.mimeType,clockRate:item.clockRate,channels:item.channels}]));
    let pixelRange = null;
    if (video?.videoWidth && video.readyState >= 2) {
      const canvas = document.createElement('canvas'); canvas.width = 48; canvas.height = 27;
      const ctx = canvas.getContext('2d'); ctx.drawImage(video, 0, 0, 48, 27);
      const rgb = Array.from(ctx.getImageData(0, 0, 48, 27).data).filter((_, index) => index % 4 !== 3);
      pixelRange = Math.max(...rgb) - Math.min(...rgb);
    }
    return {
      at: new Date().toISOString(), state: document.body.dataset.state,
      connectionState: pc?.connectionState || null, iceConnectionState: pc?.iceConnectionState || null,
      signalingState: pc?.signalingState || null,
      media: video ? {width:video.videoWidth,height:video.videoHeight,currentTime:video.currentTime,paused:video.paused,muted:video.muted,
        frames:video.getVideoPlaybackQuality().totalVideoFrames,droppedFrames:video.getVideoPlaybackQuality().droppedVideoFrames,pixelRange,
        tracks:video.srcObject?.getTracks().map(track => ({kind:track.kind,readyState:track.readyState,muted:track.muted})) || []} : null,
      sound: {disabled:document.querySelector('#sound')?.disabled,label:document.querySelector('#sound-label')?.textContent},
      inbound: stats.filter(item => item.type === 'inbound-rtp').map(item => ({kind:item.kind,bytesReceived:item.bytesReceived,packetsReceived:item.packetsReceived,
        packetsLost:item.packetsLost,jitter:item.jitter,framesDecoded:item.framesDecoded,keyFramesDecoded:item.keyFramesDecoded,
        totalAudioEnergy:item.totalAudioEnergy,totalSamplesReceived:item.totalSamplesReceived,codec:codecs[item.codecId]})),
      candidatePairs:stats.filter(item => item.type === 'candidate-pair').map(item => ({state:item.state,nominated:item.nominated,
        bytesReceived:item.bytesReceived,bytesSent:item.bytesSent,requestsSent:item.requestsSent,responsesReceived:item.responsesReceived,currentRoundTripTime:item.currentRoundTripTime})),
      peerCount:peers.length
    };
  });
}
async function sample(label) {
  const result = await snapshot(); result.label = label; report.samples.push(result); await saveReport(); return result;
}
async function awaitControl(expected, timeoutMs = 600000, phaseStartedAt = Date.now()) {
  while (Date.now() - phaseStartedAt < timeoutMs) {
    if (controlFile && fs.existsSync(controlFile)) {
      const stat = fs.statSync(controlFile);
      if (stat.mtimeMs >= phaseStartedAt && fs.readFileSync(controlFile,'utf8').trim() === expected) return true;
    }
    await sleep(1000);
  }
  return false;
}
async function recordDemo() {
  const frameDir = path.join(output, 'private-demo-frames');
  await fsp.mkdir(frameDir);
  const client = await page.createCDPSession();
  let count = 0, lastTime = 0;
  const writes = [];
  const onFrame = event => {
    client.send('Page.screencastFrameAck', {sessionId:event.sessionId}).catch(() => {});
    if (event.metadata.timestamp - lastTime < 0.095) return;
    lastTime = event.metadata.timestamp;
    writes.push(fsp.writeFile(path.join(frameDir, `${String(count++).padStart(5, '0')}.jpg`), Buffer.from(event.data, 'base64')));
  };
  client.on('Page.screencastFrame', onFrame);
  try {
    await client.send('Page.startScreencast', {format:'jpeg',quality:82,maxWidth:1280,maxHeight:1000,everyNthFrame:1});
    await sleep(2000);
    await page.click('[data-fit="fill"]'); await sleep(2000);
    await page.click('[data-fit="fit"]'); await sleep(2000);
  } finally {
    await client.send('Page.stopScreencast'); client.off('Page.screencastFrame', onFrame);
    await Promise.all(writes); await client.detach();
  }
  const ffmpeg = spawnSync('ffmpeg', ['-version'], {encoding:'utf8'});
  const result = {frames:count,audio:false,scope:'Actual Chrome receiver webpage showing synthetic content captured by Android. Page-only recording excludes URL/address bar. This recording has no audio track.'};
  if (ffmpeg.status === 0 && count > 10) {
    execFileSync('ffmpeg', ['-y','-loglevel','error','-framerate','10','-i',path.join(frameDir,'%05d.jpg'),'-vf','scale=960:-2:flags=lanczos','-c:v','libvpx-vp9','-crf','34','-b:v','0',path.join(output,'android-chrome-demo.webm')]);
    execFileSync('ffmpeg', ['-y','-loglevel','error','-framerate','10','-i',path.join(frameDir,'%05d.jpg'),'-filter_complex','[0:v]scale=720:-2:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse',path.join(output,'android-chrome-demo.gif')]);
    await fsp.rm(frameDir, {recursive:true});
    result.files = ['android-chrome-demo.webm','android-chrome-demo.gif'];
    result.files.forEach(name => generatedArtifacts.add(name));
  } else result.framesDirectory = 'private-demo-frames';
  return result;
}
async function observeSenderStop() {
  const stopReadyAt = Date.now();
  console.log('READY_FOR_ANDROID_STOP: Device owner may stop sharing, then write stop to the control file. No ADB action will be taken here.');
  report.events.push({at:new Date().toISOString(),event:'Ready for device owner to stop Android sharing'}); await saveReport();
  const signaled = await awaitControl('stop', 600000, stopReadyAt);
  if (!signaled) { report.checks.push({name:'Android Stop observed and URL revoked',passed:false,message:'Device owner stop signal not received within 600 seconds.'}); return; }
  report.events.push({at:new Date().toISOString(),event:'Device owner reported Android Stop'});
  await sleep(8000);
  const stopped = await sample('after-device-stop');
  const probe = await browser.newPage();
  try {
    const response = await probe.goto(urlText, {waitUntil:'domcontentloaded',timeout:15000});
    report.revocationProbe = {result:'http response',status:response?.status()};
    report.tokenRevoked = !!response && [401,403,404,410].includes(response.status());
    report.oldUrlNoLongerServed = report.tokenRevoked;
  } catch (error) {
    const message = safe(error.message);
    report.revocationProbe = {result:'network unavailable',message};
    // A closed listener proves the former URL is unavailable after owner-reported
    // Stop; it cannot alone distinguish token retirement from a temporary outage.
    report.oldUrlNoLongerServed = /ERR_CONNECTION_REFUSED|ERR_CONNECTION_RESET|ERR_EMPTY_RESPONSE/.test(message);
  } finally { await probe.close(); }
  report.checks.push({name:'Android Stop ends media and former URL no longer serves',passed:report.oldUrlNoLongerServed && stopped.state !== 'live',detail:{browserState:stopped.state,oldUrlNoLongerServed:report.oldUrlNoLongerServed,tokenRevoked:report.tokenRevoked}});
  console.log(report.oldUrlNoLongerServed ? 'ANDROID_URL_UNAVAILABLE: former sharing URL no longer serves. Owner must confirm token retirement before release.' : 'STOP_UNCONFIRMED: keep token and evidence private.');
}

(async () => {
  await fsp.mkdir(output, {recursive:true});
  console.log(`Private evidence directory: ${output}`);
  try {
    report.nodeVersion = process.version;
    report.puppeteerVersion = require(puppeteerModule + '/package.json').version;
    report.headless = !has('--headful');
    report.chromeArguments = ['--no-first-run','--no-default-browser-check',...(report.headless ? ['--headless=new'] : [])];
    report.puppeteerDefaultArgumentsIgnored = true;
    browser = await puppeteer.launch({executablePath:chrome,headless:report.headless,ignoreDefaultArgs:true,args:report.chromeArguments,defaultViewport:{width:1440,height:1000}});
    report.chromeVersion = await browser.version();
    page = await browser.newPage();
    page.on('pageerror', error => report.errors.push({type:'pageerror',message:safe(error.message)}));
    page.on('response', response => { responseTasks.push((async () => {
      try {
        const resource = new URL(response.url());
        if (resource.origin !== target.origin) return;
        const base = target.pathname.endsWith('/') ? target.pathname : target.pathname.slice(0,target.pathname.lastIndexOf('/')+1);
        const name = resource.pathname === base ? 'index.html' : resource.pathname.slice(base.length);
        if (!['index.html','receiver.js','receiver.css'].includes(name) || response.status() !== 200) return;
        const data = await response.buffer();
        report.servedAssets[name] = {bytes:data.length,sha256:crypto.createHash('sha256').update(data).digest('hex')};
        if (name === 'index.html') report.securityHeaders = Object.fromEntries(Object.entries(response.headers()).filter(([key]) => ['content-security-policy','permissions-policy','referrer-policy'].includes(key)));
      } catch (_) { /* Diagnostic collection must not affect page requests. */ }
    })()); });
    await page.evaluateOnNewDocument(() => {
      const NativePeer = window.RTCPeerConnection;
      window.__androidTestPeers = []; window.__androidTestCsp = [];
      addEventListener('securitypolicyviolation', event => window.__androidTestCsp.push({directive:event.effectiveDirective}));
      if (NativePeer) window.RTCPeerConnection = class extends NativePeer { constructor(...args) { super(...args); window.__androidTestPeers.push(this); } };
    });
    await page.goto(urlText, {waitUntil:'networkidle0',timeout:20000});
    report.browserPolicy = await page.evaluate(() => ({isSecureContext,rtcPeerConnectionAvailable:typeof RTCPeerConnection==='function',getUserMediaAvailable:!!navigator.mediaDevices?.getUserMedia}));
    await page.click('#connect');
    await check('Real Android video arrives over unmodified WebRTC signaling', async () => {
      await page.waitForFunction(() => document.body.dataset.state === 'live' && document.querySelector('#screen').videoWidth > 0, {timeout:45000});
      const first = await sample('first-live'); await sleep(3000); const second = await sample('video-after-3s');
      assert.ok(second.media.frames > first.media.frames, 'Decoded frame count must advance');
      assert.ok(second.media.currentTime > first.media.currentTime, 'Media time must advance');
      assert.ok(second.media.pixelRange > 80, 'Received test picture must contain visible variation');
      assert.ok(second.inbound.some(item => item.kind === 'video' && item.bytesReceived > 0 && item.framesDecoded > 0));
      assert.ok(second.candidatePairs.some(item => item.nominated && item.state === 'succeeded'));
    });
    if (report.expectedAudio) await check('User gesture enables captured Android audio with nonzero energy', async () => {
      await page.waitForFunction(() => !document.querySelector('#sound').disabled, {timeout:12000});
      if (await page.$eval('#screen', element => element.muted)) await page.click('#sound');
      await page.waitForFunction(async () => {
        const pc = [...window.__androidTestPeers].reverse().find(item => item.connectionState === 'connected');
        return pc && [...(await pc.getStats()).values()].some(item => item.type === 'inbound-rtp' && item.kind === 'audio' && item.totalAudioEnergy > 0);
      }, {timeout:20000});
      const sound = await sample('after-unmute');
      assert.equal(sound.media.muted,false); assert.equal(sound.media.paused,false);
      await sleep(2000);
      const later = await sample('audio-after-2s');
      const firstAudio = sound.inbound.find(item => item.kind === 'audio');
      const laterAudio = later.inbound.find(item => item.kind === 'audio');
      assert.ok(firstAudio && laterAudio && laterAudio.bytesReceived > firstAudio.bytesReceived && laterAudio.totalSamplesReceived > firstAudio.totalSamplesReceived && laterAudio.totalAudioEnergy > firstAudio.totalAudioEnergy, 'Audio bytes, decoded samples and energy must advance');
      return {audibility:'Nonzero decoded RTP energy verified; physical speaker listening not independently measured.'};
    });
    if ((await snapshot()).state === 'live') {
      await check('Real Android picture remains live during framing controls', async () => {
        const before = await sample('before-framing');
        await page.click('[data-fit="fill"]'); await sleep(1000);
        await page.click('[data-fit="fit"]'); await sleep(1000);
        const after = await sample('after-framing');
        assert.equal(after.state,'live'); assert.ok(after.media.frames > before.media.frames);
      });
      await page.screenshot({path:path.join(output,'android-chrome-receiver.png'),fullPage:true});
      generatedArtifacts.add('android-chrome-receiver.png');
      if (has('--record') && report.checks.find(item => item.name === 'Real Android video arrives over unmodified WebRTC signaling')?.passed) {
        const reviewReadyAt = Date.now();
        report.recordingStatus = 'waiting-for-content-confirmation';
        await saveReport();
        console.log('READY_FOR_CONTENT_REVIEW: inspect private screenshot; owner writes record to control file only after confirming synthetic-only source content.');
        const confirmed = await awaitControl('record', 600000, reviewReadyAt);
        report.contentConfirmedByOwner = confirmed;
        if (confirmed) {
          report.recordingStatus = 'recording'; await saveReport();
          report.demo = await recordDemo();
          const encoded = Array.isArray(report.demo.files) && report.demo.files.length === 2 && report.demo.files.every(name => {
            const file = path.join(output,name);
            return fs.existsSync(file) && fs.statSync(file).isFile() && fs.statSync(file).size > 0;
          });
          report.recordingStatus = encoded ? 'recorded-gif-and-webm' : 'frames-only-encoding-unavailable';
          report.checks.push({name:'Requested real receiver screencast encoded as GIF and WebM',passed:encoded,detail:{frames:report.demo.frames}});
          console.log(encoded ? `ANDROID_RECORDING_SAVED: GIF and WebM encoded from ${report.demo.frames} actual receiver frames, no audio track.` : 'ANDROID_RECORDING_INCOMPLETE: raw frames captured, GIF/WebM encoding unavailable.');
        } else {
          report.recordingStatus = 'skipped-confirmation-timeout';
          report.checks.push({name:'Recording source content confirmed by owner',passed:false,message:'No fresh record confirmation received within 600 seconds; recording skipped.'});
          console.log('ANDROID_RECORDING_SKIPPED: confirmation timed out; no GIF or WebM was generated.');
        }
      }
      await sample('after-evidence');
    }
    if (has('--record') && report.recordingStatus === 'requested-not-started') {
      report.recordingStatus = 'skipped-no-live-video';
      report.checks.push({name:'Requested real receiver screencast completed',passed:false,message:'Receiver was not live when recording was due; no recording was created.'});
    }
    report.cspViolations = await page.evaluate(() => window.__androidTestCsp || []);
    await saveReport();
    console.log(`ANDROID_MEDIA_EVIDENCE_READY: report and private screenshot saved; recording status = ${report.recordingStatus}.`);
    if (has('--await-stop')) await observeSenderStop();
    else if ((await snapshot()).state === 'live') await page.click('#disconnect');
    report.passed = report.checks.length > 0 && report.checks.every(item => item.passed) && report.errors.length === 0 && report.cspViolations.length === 0 && (!has('--record') || report.recordingStatus === 'recorded-gif-and-webm');
  } catch (error) {
    if (report.recordingStatus === 'recording') report.recordingStatus = 'failed-recording-or-encoding';
    report.passed = false; report.failure = {message:safe(error.message)};
    if (page) report.failure.lastSample = await snapshot().catch(() => null);
    console.error(`Driver failure: ${safe(error.message)}`);
  } finally {
    await Promise.allSettled(responseTasks);
    if (browser) await browser.close();
    report.finishedAt = new Date().toISOString();
    for (const name of generatedArtifacts) {
      const file = path.join(output,name);
      if (fs.existsSync(file)) { const data = fs.readFileSync(file); artifactHashes[name] = {bytes:data.length,sha256:crypto.createHash('sha256').update(data).digest('hex')}; }
    }
    report.artifacts = artifactHashes;
    await saveReport();
    console.log(`Evidence directory: ${output}`);
    if (!report.passed) process.exitCode = 1;
  }
})().catch(error => { console.error(safe(error.message)); process.exitCode = 1; });
