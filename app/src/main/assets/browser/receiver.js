/* MoonCast browser receiver. GPL-3.0; browser APIs only, no external runtime. */
(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const video = $('screen');
  const stage = $('stage');
  const strings = {
    en: {
      preview: 'Preview', local: 'Local connection', language: 'Language',
      eyebrow: 'ANDROID → BROWSER', title: 'A bigger view. Right here.',
      connect: 'Connect', disconnect: 'Disconnect', live: 'LIVE', fit: 'Fit', fill: 'Fill',
      zoom: 'Zoom', reset: 'Reset', play: 'Play video', fullscreen: 'Enter fullscreen', exitFullscreen: 'Exit fullscreen',
      panHint: 'Drag the picture to pan · Fit preserves the whole frame',
      privacy: 'Only the screen and audio allowed by Android and the source app can be shared. Protected content may appear black or silent.',
      network: 'Keep both devices on the same trusted network. Keep this private session link to yourself.',
      idle: 'Ready to connect', connecting: 'Connecting…', liveState: 'Connected', reconnecting: 'Reconnecting…', error: 'Connection interrupted',
      emptyTitle: 'Your screen belongs here', emptyDescription: 'Keep MoonCast sharing on your Android device, then connect.',
      connectingTitle: 'Finding your screen', connectingDescription: 'Establishing a direct connection to your Android device…',
      reconnectTitle: 'Bringing your screen back', reconnectDescription: 'Keep MoonCast running and both devices on the same network.',
      errorTitle: 'Let’s reconnect', defaultHint: 'Video starts muted. Enable sound when you’re ready.',
      soundOn: 'Sound on', soundOff: 'Enable sound', audioUnavailable: 'No shared audio',
      soundHint: 'Sound enabled. The source app may still prevent audio capture.',
      silentHint: 'This sender is sharing video only. Audio capture must be enabled on Android before starting.',
      mutedHint: 'Sound is muted in this browser.', playHint: 'Your browser paused playback. Select Play video to continue.',
      retryHint: 'Retry {attempt} of {max} in {seconds}s. Keep the Android sharing session open.',
      exhausted: 'Reconnection paused after {max} attempts. Check the phone and network, then select Connect.',
      unsupported: 'This browser does not support WebRTC receiving. Try a current Chrome browser.',
      identityUnavailable: 'This browser cannot create a secure session identifier. Try a current Chrome browser.',
      iceTimeout: 'Local network addresses could not be gathered in time. Check the network and try again.',
      networkError: 'Cannot reach MoonCast. Keep sharing active and check that both devices are on the same network.',
      expired: 'This sharing link has expired or was stopped. Open the current link shown by MoonCast.',
      busy: 'Another browser is connected. Disconnect it first, then select Connect.',
      invalidAnswer: 'MoonCast returned an invalid connection response. Stop and restart browser sharing on Android.',
      mediaTimeout: 'No video arrived. Check Android sharing and local network device access.',
      playbackError: 'Playback could not start. Select Play video, or reconnect.',
      fullscreenError: 'Fullscreen is unavailable here. You can still resize the browser window.',
      framing: 'Picture framing', stageLabel: 'Screen. Arrow keys to pan; plus or minus to zoom; 0 to reset; F for fullscreen.',
      videoLabel: 'Shared Android screen', viewerLabel: 'Screen receiver', pageTitle: 'MoonCast · Browser receiver'
    },
    zh: {
      preview: '预览版', local: '局域网连接', language: '语言',
      eyebrow: 'ANDROID → 浏览器', title: '更大的视野，就在这里。',
      connect: '连接', disconnect: '断开', live: '直播', fit: '适应', fill: '填充',
      zoom: '缩放', reset: '重置', play: '播放画面', fullscreen: '进入全屏', exitFullscreen: '退出全屏',
      panHint: '拖动画面可平移 · 适应模式保留完整画面',
      privacy: '仅分享 Android 和源应用允许采集的画面与声音。受保护的内容可能显示为黑屏或无声。',
      network: '请将两台设备接入同一可信局域网，并妥善保管此私人会话链接。',
      idle: '等待连接', connecting: '连接中…', liveState: '已连接', reconnecting: '重新连接中…', error: '连接已中断',
      emptyTitle: '让手机画面来到这里', emptyDescription: '在 Android 设备上保持 MoonCast 分享，然后点击连接。',
      connectingTitle: '正在连接手机画面', connectingDescription: '正在与 Android 设备建立直接连接…',
      reconnectTitle: '正在恢复画面', reconnectDescription: '请保持 MoonCast 运行，并将两台设备接入同一网络。',
      errorTitle: '重新连接，继续分享', defaultHint: '画面默认静音播放。准备好后，点击开启声音。',
      soundOn: '声音已开启', soundOff: '开启声音', audioUnavailable: '未分享声音',
      soundHint: '已开启声音。源应用仍可能禁止采集音频。',
      silentHint: '发送端当前仅分享画面。如需声音，请先在 Android 上启用音频采集，再开始分享。',
      mutedHint: '此浏览器已静音。', playHint: '浏览器暂停了播放，请点击“播放画面”继续。',
      retryHint: '将在 {seconds} 秒后第 {attempt}/{max} 次重试。请保持 Android 分享会话开启。',
      exhausted: '已重试 {max} 次，自动重连已暂停。检查手机和网络后，请点击连接。',
      unsupported: '此浏览器不支持 WebRTC 接收，请尝试较新的 Chrome 浏览器。',
      identityUnavailable: '此浏览器无法创建安全的会话标识，请尝试较新的 Chrome 浏览器。',
      iceTimeout: '未能及时获取局域网连接地址。请检查网络后重试。',
      networkError: '无法连接 MoonCast。请保持分享开启，并检查两台设备是否位于同一网络。',
      expired: '此分享链接已失效或停止。请打开 MoonCast 当前显示的链接。',
      busy: '另一个浏览器正在接收。请先断开它，再点击连接。',
      invalidAnswer: 'MoonCast 返回了无效的连接响应。请在 Android 上停止并重新开启浏览器分享。',
      mediaTimeout: '尚未收到画面。请检查 Android 分享状态及局域网设备互访设置。',
      playbackError: '无法开始播放。请点击“播放画面”，或重新连接。',
      fullscreenError: '当前无法进入全屏，仍可调整浏览器窗口大小。',
      framing: '画面适配', stageLabel: '画面。方向键平移，加减号缩放，0 重置，F 全屏。',
      videoLabel: '分享的 Android 画面', viewerLabel: '画面接收端', pageTitle: 'MoonCast · 浏览器接收端'
    }
  };
  const read = (store, key) => { try { return window[store].getItem(key); } catch (_) { return null; } };
  const write = (store, key, value) => { try { value === null ? window[store].removeItem(key) : window[store].setItem(key, value); } catch (_) { /* Private browsing can disable storage. */ } };
  const linkLanguage = new URLSearchParams(location.search).get('lang');
  let language = read('localStorage', 'mooncast.browser.language') ||
    (['zh', 'zh-CN', 'en'].includes(linkLanguage) ? (linkLanguage.startsWith('zh') ? 'zh' : 'en') :
      (navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en'));
  if (!strings[language]) language = 'en';
  const text = (key, values = {}) => Object.entries(values).reduce((result, [name, value]) => result.replaceAll(`{${name}}`, String(value)), strings[language][key] || key);
  const sessionKey = `mooncast.browser.session:${location.pathname}`;
  const viewerKey = `${sessionKey}:viewer`;
  let viewerId = read('sessionStorage', viewerKey);
  if (typeof viewerId !== 'string' || !/^[a-f0-9]{32}$/.test(viewerId)) {
    try {
      viewerId = Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, '0')).join('');
      write('sessionStorage', viewerKey, viewerId);
    } catch (_) { viewerId = null; }
  }
  const validId = (value) => typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value);
  let sessionId = read('sessionStorage', sessionKey);
  if (!validId(sessionId)) sessionId = null;
  let state = 'idle', hint = 'defaultHint', hintValues = {}, errorDetail = 'networkError';
  let peer = null, controller = null, generation = 0, wanted = false, retry = 0;
  let retryTimer = null, heartbeatTimer = null, connectionTimer = null, disconnectTimer = null, stableTimer = null;
  let hasAudio = false, audioAllowed = false, framing = 'fit', zoom = 1, panX = 0, panY = 0, drag = null;
  const MAX_RETRIES = 6;

  function setHint(key, values = {}) { hint = key; hintValues = values; $('hint').textContent = text(key, values); }
  function renderLanguage() {
    document.documentElement.lang = language === 'zh' ? 'zh-CN' : 'en';
    document.title = text('pageTitle');
    $('language').value = language;
    document.querySelectorAll('[data-i18n]').forEach((element) => { element.textContent = text(element.dataset.i18n); });
    stage.setAttribute('aria-label', text('stageLabel'));
    video.setAttribute('aria-label', text('videoLabel'));
    $('viewer').setAttribute('aria-label', text('viewerLabel'));
    $('fit-controls').setAttribute('aria-label', text('framing'));
    renderState(); renderSound(); renderFullscreen();
    setHint(hint, hintValues);
  }
  function renderState() {
    document.body.dataset.state = state;
    $('app').dataset.state = state;
    $('status-text').textContent = text(state === 'live' ? 'liveState' : state);
    $('connect').hidden = wanted;
    $('disconnect').hidden = !wanted;
    $('empty').hidden = state === 'live';
    $('stream-info').hidden = state !== 'live';
    $('empty-title').textContent = text(state === 'connecting' ? 'connectingTitle' : state === 'reconnecting' ? 'reconnectTitle' : state === 'error' ? 'errorTitle' : 'emptyTitle');
    $('empty-description').textContent = text(state === 'connecting' ? 'connectingDescription' : state === 'reconnecting' ? 'reconnectDescription' : state === 'error' ? errorDetail : 'emptyDescription', { max: MAX_RETRIES });
  }
  function setState(value) { state = value; renderState(); }
  function renderSound() {
    $('sound').disabled = !hasAudio;
    $('sound').setAttribute('aria-pressed', String(hasAudio && !video.muted));
    $('sound-label').textContent = text(!audioAllowed && sessionId && wanted ? 'audioUnavailable' : video.muted ? 'soundOff' : 'soundOn');
  }
  function renderFullscreen() {
    const label = text(document.fullscreenElement ? 'exitFullscreen' : 'fullscreen');
    $('fullscreen').setAttribute('aria-label', label); $('fullscreen').title = label;
  }
  function renderFrame() {
    const width = stage.clientWidth, height = stage.clientHeight;
    if (!video.videoWidth || !video.videoHeight || !width || !height) return;
    const scale = (framing === 'fill' ? Math.max : Math.min)(width / video.videoWidth, height / video.videoHeight);
    const frameWidth = video.videoWidth * scale, frameHeight = video.videoHeight * scale;
    panX = Math.max(-Math.max(0, (frameWidth * zoom - width) / 2), Math.min(Math.max(0, (frameWidth * zoom - width) / 2), panX));
    panY = Math.max(-Math.max(0, (frameHeight * zoom - height) / 2), Math.min(Math.max(0, (frameHeight * zoom - height) / 2), panY));
    video.style.width = `${frameWidth}px`; video.style.height = `${frameHeight}px`;
    video.style.transform = `translate(-50%, -50%) translate(${panX}px, ${panY}px) scale(${zoom})`;
    $('resolution').textContent = `${video.videoWidth} × ${video.videoHeight}`;
  }
  function setZoom(value) {
    zoom = Math.max(1, Math.min(4, value)); $('zoom').value = String(zoom);
    $('zoom-value').textContent = `${Math.round(zoom * 100)}%`; renderFrame();
  }
  function resetFrame() { panX = 0; panY = 0; setZoom(1); }
  function setFraming(value) {
    framing = value;
    document.querySelectorAll('[data-fit]').forEach((button) => {
      const selected = button.dataset.fit === framing;
      button.classList.toggle('selected', selected); button.setAttribute('aria-pressed', String(selected));
    });
    resetFrame();
  }
  function fail(key, fatal = false) { const error = new Error(key); error.key = key; error.fatal = fatal; return error; }
  async function request(path, options = {}) {
    const abort = new AbortController();
    const parent = options.signal;
    const onAbort = () => abort.abort();
    if (parent) { if (parent.aborted) abort.abort(); else parent.addEventListener('abort', onAbort, { once: true }); }
    const timeout = setTimeout(() => abort.abort(), 15000);
    try {
      const response = await fetch(new URL(path, location.href), { ...options, signal: abort.signal, cache: 'no-store', credentials: 'same-origin', referrerPolicy: 'no-referrer' });
      if (!response.ok) {
        if (response.status === 401 || response.status === 403 || response.status === 410 ||
          (response.status === 404 && options.method === 'POST')) throw fail('expired', true);
        if (response.status === 409) throw fail('busy', true);
        throw fail('networkError');
      }
      return response.status === 204 ? null : await response.json();
    } catch (error) {
      if (error.key) throw error;
      throw fail('networkError');
    } finally { clearTimeout(timeout); if (parent) parent.removeEventListener('abort', onAbort); }
  }
  function release(id, keepalive = false) {
    if (!validId(id)) return;
    fetch(new URL(`session/${encodeURIComponent(id)}`, location.href), { method: 'DELETE', keepalive, credentials: 'same-origin', referrerPolicy: 'no-referrer' }).catch(() => {});
  }
  function closePeer() {
    generation++;
    if (controller) { controller.abort(); controller = null; }
    [retryTimer, heartbeatTimer, connectionTimer, disconnectTimer, stableTimer].forEach(clearTimeout);
    retryTimer = heartbeatTimer = connectionTimer = disconnectTimer = stableTimer = null;
    if (peer) { peer.ontrack = null; peer.onconnectionstatechange = null; peer.oniceconnectionstatechange = null; peer.close(); peer = null; }
    if (video.srcObject) video.srcObject.getTracks().forEach((track) => track.stop());
    video.srcObject = null; $('play').hidden = true; stage.classList.remove('has-video');
    hasAudio = false; audioAllowed = false; renderSound();
  }
  function stop() {
    wanted = false; closePeer(); release(sessionId, true); sessionId = null;
    write('sessionStorage', sessionKey, null); video.muted = true; retry = 0;
    setState('idle'); setHint('defaultHint'); renderSound();
  }
  function handleFailure(error) {
    if (!wanted) return;
    closePeer(); errorDetail = error.key || 'networkError';
    if (error.fatal || retry >= MAX_RETRIES) {
      wanted = false;
      if (!error.fatal) errorDetail = 'exhausted';
      setState('error'); setHint(errorDetail, { max: MAX_RETRIES });
      release(sessionId); sessionId = null; write('sessionStorage', sessionKey, null); renderSound();
      return;
    }
    retry++;
    const delay = Math.min(20000, 1000 * (2 ** (retry - 1)));
    setState('reconnecting'); setHint('retryHint', { attempt: retry, max: MAX_RETRIES, seconds: delay / 1000 });
    retryTimer = setTimeout(() => { retryTimer = null; connect(false); }, delay);
  }
  function gatherIce(connection, signal) {
    if (connection.iceGatheringState === 'complete') return Promise.resolve();
    return new Promise((resolve, reject) => {
      const finish = (error) => { clearTimeout(timeout); connection.removeEventListener('icegatheringstatechange', changed); signal.removeEventListener('abort', aborted); error ? reject(error) : resolve(); };
      const changed = () => { if (connection.iceGatheringState === 'complete') finish(); };
      const aborted = () => finish(fail('networkError'));
      const timeout = setTimeout(() => finish(fail('iceTimeout')), 12000);
      connection.addEventListener('icegatheringstatechange', changed);
      signal.addEventListener('abort', aborted, { once: true });
      if (signal.aborted) aborted(); else changed();
    });
  }
  function maybeLive() {
    if (!wanted || !peer || peer.connectionState !== 'connected' || video.readyState < 2 || !video.videoWidth) return;
    if (state !== 'live') {
      clearTimeout(connectionTimer); connectionTimer = null;
      setState('live'); setHint(!audioAllowed ? 'silentHint' : video.muted ? 'defaultHint' : 'soundHint');
      stableTimer = setTimeout(() => { retry = 0; stableTimer = null; }, 20000);
    }
    stage.classList.add('has-video'); renderFrame();
  }
  async function heartbeat(current) {
    if (current !== generation || !wanted || !sessionId) return;
    try {
      const response = await request(`status/${encodeURIComponent(sessionId)}`, { signal: controller.signal });
      if (current !== generation) return;
      if (response && ['failed', 'closed', 'stopped', 'missing'].includes(response.state)) throw fail('networkError');
      if (response && typeof response.audio === 'boolean' && response.audio !== audioAllowed) {
        audioAllowed = response.audio;
        hasAudio = audioAllowed && !!video.srcObject?.getAudioTracks().some((track) => track.readyState === 'live');
        if (!audioAllowed) video.muted = true;
        renderSound();
        setHint(audioAllowed ? (video.muted ? 'defaultHint' : 'soundHint') : 'silentHint');
      }
      heartbeatTimer = setTimeout(() => heartbeat(current), 5000);
    } catch (error) { if (current === generation) handleFailure(error); }
  }
  async function connect(userInitiated) {
    if (userInitiated) { retry = 0; wanted = true; }
    if (!wanted) return;
    if (!window.RTCPeerConnection || !window.MediaStream) { handleFailure(fail('unsupported', true)); return; }
    if (!viewerId) { handleFailure(fail('identityUnavailable', true)); return; }
    closePeer();
    const current = generation;
    controller = new AbortController();
    const signal = controller.signal;
    setState(retry ? 'reconnecting' : 'connecting');
    if (!retry) setHint('defaultHint');
    connectionTimer = setTimeout(() => { if (current === generation) handleFailure(fail('mediaTimeout')); }, 35000);
    try {
      const connection = new RTCPeerConnection({ iceServers: [], bundlePolicy: 'max-bundle' });
      peer = connection;
      const stream = new MediaStream(); video.srcObject = stream;
      connection.addTransceiver('video', { direction: 'recvonly' });
      connection.addTransceiver('audio', { direction: 'recvonly' });
      connection.ontrack = (event) => {
        if (current !== generation) return;
        stream.addTrack(event.track);
        if (event.track.kind === 'audio') { hasAudio = audioAllowed; renderSound(); }
        event.track.onended = () => {
          if (current !== generation) return;
          if (event.track.kind === 'video') handleFailure(fail('networkError'));
          else { hasAudio = audioAllowed = false; video.muted = true; renderSound(); setHint('silentHint'); }
        };
        video.play().then(() => { if (current === generation) { $('play').hidden = true; maybeLive(); } }).catch(() => { if (current === generation) { $('play').hidden = false; setHint('playHint'); } });
      };
      const changed = () => {
        if (current !== generation) return;
        if (connection.connectionState === 'failed' || connection.iceConnectionState === 'failed') { handleFailure(fail('networkError')); return; }
        if (connection.connectionState === 'disconnected' || connection.iceConnectionState === 'disconnected') {
          if (!disconnectTimer) disconnectTimer = setTimeout(() => { if (current === generation) handleFailure(fail('networkError')); }, 4000);
        } else { clearTimeout(disconnectTimer); disconnectTimer = null; }
        maybeLive();
      };
      connection.onconnectionstatechange = changed; connection.oniceconnectionstatechange = changed;
      await connection.setLocalDescription(await connection.createOffer());
      await gatherIce(connection, signal);
      if (current !== generation) return;
      const answer = await request('session', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sdp: connection.localDescription.sdp, previousSessionId: sessionId, viewerId }), signal });
      if (current !== generation) { if (answer && validId(answer.id)) release(answer.id); return; }
      if (!answer || !validId(answer.id) || answer.type !== 'answer' || typeof answer.sdp !== 'string' || answer.sdp.length > 262144) throw fail('invalidAnswer', true);
      sessionId = answer.id; write('sessionStorage', sessionKey, sessionId);
      audioAllowed = answer.audio === true;
      await connection.setRemoteDescription({ type: 'answer', sdp: answer.sdp });
      if (current !== generation) return;
      hasAudio = audioAllowed && stream.getAudioTracks().some((track) => track.readyState === 'live'); renderSound();
      heartbeatTimer = setTimeout(() => heartbeat(current), 5000);
      maybeLive();
    } catch (error) { if (current === generation) handleFailure(error.key ? error : fail('networkError')); }
  }

  $('connect').addEventListener('click', () => connect(true));
  $('disconnect').addEventListener('click', stop);
  $('sound').addEventListener('click', async () => {
    if (!hasAudio) return;
    video.muted = !video.muted;
    try { await video.play(); setHint(video.muted ? 'mutedHint' : 'soundHint'); }
    catch (_) { video.muted = true; $('play').hidden = false; setHint('playHint'); }
    renderSound();
  });
  $('play').addEventListener('click', async () => {
    try { await video.play(); $('play').hidden = true; maybeLive(); }
    catch (_) { setHint('playbackError'); }
  });
  $('language').addEventListener('change', (event) => { language = event.target.value; write('localStorage', 'mooncast.browser.language', language); renderLanguage(); });
  document.querySelectorAll('[data-fit]').forEach((button) => button.addEventListener('click', () => setFraming(button.dataset.fit)));
  $('zoom').addEventListener('input', (event) => setZoom(Number(event.target.value)));
  $('reset').addEventListener('click', resetFrame);
  async function fullscreen() {
    try { if (document.fullscreenElement) await document.exitFullscreen(); else await $('viewer').requestFullscreen(); }
    catch (_) { setHint('fullscreenError'); }
  }
  $('fullscreen').addEventListener('click', fullscreen);
  document.addEventListener('fullscreenchange', () => { renderFullscreen(); renderFrame(); });
  ['loadeddata', 'playing', 'resize'].forEach((event) => video.addEventListener(event, () => { renderFrame(); maybeLive(); }));
  video.addEventListener('pause', () => { if (wanted && peer) { $('play').hidden = false; setHint('playHint'); } });
  video.addEventListener('error', () => { if (wanted) setHint('playbackError'); });
  window.addEventListener('resize', renderFrame);
  if (window.ResizeObserver) new ResizeObserver(renderFrame).observe(stage);
  stage.addEventListener('pointerdown', (event) => {
    if (state !== 'live' || event.target.closest('button') || event.button !== 0) return;
    drag = { x: event.clientX, y: event.clientY, panX, panY }; stage.setPointerCapture(event.pointerId); stage.classList.add('dragging');
  });
  stage.addEventListener('pointermove', (event) => {
    if (!drag) return;
    panX = drag.panX + event.clientX - drag.x; panY = drag.panY + event.clientY - drag.y; renderFrame();
  });
  const endDrag = () => { drag = null; stage.classList.remove('dragging'); };
  stage.addEventListener('pointerup', endDrag); stage.addEventListener('pointercancel', endDrag); stage.addEventListener('lostpointercapture', endDrag);
  stage.addEventListener('keydown', (event) => {
    if (event.target !== stage || event.altKey || event.ctrlKey || event.metaKey) return;
    const actions = { ArrowLeft: () => { panX -= 40; }, ArrowRight: () => { panX += 40; }, ArrowUp: () => { panY -= 40; }, ArrowDown: () => { panY += 40; }, '+': () => setZoom(zoom + .1), '=': () => setZoom(zoom + .1), '-': () => setZoom(zoom - .1), '0': resetFrame, f: fullscreen, F: fullscreen };
    if (actions[event.key]) { event.preventDefault(); actions[event.key](); renderFrame(); }
  });
  // Reload keeps the id so the same tab can replace its own session. Explicit disconnect
  // sends DELETE; the sender's heartbeat lease cleans up a tab/browser that disappears.
  window.addEventListener('pagehide', () => { wanted = false; closePeer(); });
  window.addEventListener('pageshow', (event) => { if (event.persisted) { setState('idle'); setHint('defaultHint'); } });
  renderLanguage();
  if (!window.RTCPeerConnection || !window.MediaStream) { errorDetail = 'unsupported'; setState('error'); setHint('unsupported'); $('connect').disabled = true; }
  else if (!viewerId) { errorDetail = 'identityUnavailable'; setState('error'); setHint('identityUnavailable'); $('connect').disabled = true; }
})();
