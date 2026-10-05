/** Public H5 page for a shared short video. The mp4 fills the screen; download and invite sit on the empty area. */

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function safeVideoUrl(raw) {
  const url = String(raw || '').trim()
  if (!/^https?:\/\//i.test(url)) return ''
  if (/[\s"'<>]/.test(url)) return ''
  return url
}

function safeInviteCode(raw) {
  const code = String(raw || '').trim().toLowerCase()
  if (!/^[a-z0-9]{4,32}$/.test(code)) return ''
  return code
}

export function renderWatchPage({ videoUrl, inviteCode, title } = {}) {
  const video = safeVideoUrl(videoUrl)
  const code = safeInviteCode(inviteCode)
  const heading = String(title || '').trim().slice(0, 80) || '英语短视频'
  const safeTitle = escapeHtml(heading)
  const safeCode = escapeHtml(code)
  const safeVideo = escapeHtml(video)
  const player = video
    ? `<video id="player" class="player" src="${safeVideo}" autoplay playsinline webkit-playsinline x5-playsinline x5-video-player-type="h5-page" x5-video-player-fullscreen="false" preload="auto"></video>
       <button type="button" class="play" id="playBtn" aria-label="播放"><svg viewBox="0 0 24 24" width="28" height="28" aria-hidden="true"><polygon points="9,5 20,12 9,19"/></svg></button>
       <button type="button" class="unmute" id="unmuteBtn">打开声音</button>`
    : `<p class="missing">这条视频暂时无法播放</p>`
  const inviteBlock = code
    ? `<div class="invite">
        <span class="label">邀请码</span>
        <span class="code" id="code">${safeCode}</span>
        <button type="button" class="copy" id="copyBtn">复制</button>
      </div>`
    : ''

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
  <meta name="theme-color" content="#000000" />
  <title>${safeTitle} · 词搭子</title>
  <style>
    * { box-sizing: border-box; }
    html, body {
      margin: 0;
      height: 100%;
      background: #000;
      color: #fff;
      font-family: "PingFang SC", "Noto Sans SC", system-ui, sans-serif;
      overflow: hidden;
    }
    .stage { position: fixed; inset: 0; background: #000; }
    .player {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
      background: #000;
    }
    .missing {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0;
      color: #8aa3b5;
    }
    .play {
      display: none;
      align-items: center;
      justify-content: center;
      position: absolute;
      left: 50%;
      top: 46%;
      transform: translate(-50%, -50%);
      width: 74px;
      height: 74px;
      border-radius: 50%;
      border: 3px solid #fff;
      background: rgba(0, 0, 0, 0.28);
      z-index: 3;
      padding: 0;
      line-height: 0;
    }
    .play svg { display: block; fill: #fff; margin-left: 3px; }
    .unmute {
      display: none;
      position: absolute;
      left: 50%;
      top: 46%;
      transform: translate(-50%, -50%);
      z-index: 3;
      border: 0;
      border-radius: 999px;
      padding: 10px 18px;
      background: rgba(0, 0, 0, 0.62);
      color: #fff;
      font-size: 15px;
      font-weight: 700;
    }
    .hud {
      position: absolute;
      left: 12px;
      right: 12px;
      bottom: calc(12px + env(safe-area-inset-bottom));
      z-index: 4;
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 14px;
      border-radius: 16px;
      background: rgba(0, 0, 0, 0.62);
    }
    .hud.in-gap {
      background: #000;
      justify-content: center;
    }
    .invite {
      display: flex;
      align-items: center;
      gap: 10px;
      min-height: 44px;
    }
    .label { color: #fff; font-size: 14px; font-weight: 700; flex: none; }
    .code {
      color: #f0c36a;
      font-size: 22px;
      font-weight: 800;
      letter-spacing: 0.06em;
      word-break: break-all;
    }
    .copy {
      margin-left: auto;
      flex: none;
      border: 1px solid rgba(61, 214, 245, 0.7);
      background: transparent;
      color: #7fe7ff;
      border-radius: 999px;
      padding: 6px 14px;
      font-size: 14px;
      font-weight: 700;
    }
    .apk {
      display: block;
      text-align: center;
      text-decoration: none;
      border-radius: 12px;
      padding: 13px 14px;
      font-size: 17px;
      font-weight: 800;
      color: #041018;
      background: linear-gradient(135deg, #3dd6f5, #1b9fc4);
    }
  </style>
</head>
<body>
  <div class="stage">
    ${player}
    <div class="hud" id="hud">
      ${inviteBlock}
      <a class="apk" href="/app/WordBuddy-release.apk">下载词搭子</a>
    </div>
  </div>
  <script>
    var video = document.getElementById('player');
    var playBtn = document.getElementById('playBtn');
    var unmuteBtn = document.getElementById('unmuteBtn');
    var hud = document.getElementById('hud');
    var playedOnce = false;
    function syncPlay() {
      if (!playBtn || !video) return;
      playBtn.style.display = video.paused ? 'flex' : 'none';
    }
    function showUnmute() { if (unmuteBtn) unmuteBtn.style.display = 'block'; }
    function hideUnmute() { if (unmuteBtn) unmuteBtn.style.display = 'none'; }
    var playToken = 0;
    function kick() {
      if (!video) return;
      var mine = ++playToken;
      var pending = video.play();
      if (!pending || !pending.then) return;
      pending.then(function () {
        if (mine !== playToken) return;
        if (!video.muted) hideUnmute();
      }).catch(function () {
        if (mine !== playToken) return;
        video.muted = true;
        var again = video.play();
        if (!again || !again.then) return;
        again.then(showUnmute).catch(function () {
          playedOnce = true;
          syncPlay();
        });
      });
    }
    function boot() {
      kick();
      function withSound() {
        video.muted = false;
        hideUnmute();
        kick();
      }
      if (window.WeixinJSBridge) {
        WeixinJSBridge.invoke('getNetworkType', {}, withSound);
      } else {
        document.addEventListener('WeixinJSBridgeReady', function () {
          WeixinJSBridge.invoke('getNetworkType', {}, withSound);
        }, false);
      }
    }
    function placeHud() {
      if (!hud || !video || !video.videoWidth || !video.videoHeight) return;
      var sw = window.innerWidth;
      var sh = window.innerHeight;
      var scale = Math.min(sw / video.videoWidth, sh / video.videoHeight);
      var dispH = video.videoHeight * scale;
      var gap = (sh - dispH) / 2;
      if (gap >= hud.offsetHeight + 8) {
        hud.className = 'hud in-gap';
        hud.style.top = (sh - gap) + 'px';
        hud.style.bottom = '0';
        hud.style.height = gap + 'px';
      } else {
        hud.className = 'hud';
        hud.style.top = 'auto';
        hud.style.height = 'auto';
        hud.style.bottom = '0';
      }
    }
    if (video && playBtn) {
      playBtn.onclick = function (event) {
        event.stopPropagation();
        video.muted = false;
        hideUnmute();
        kick();
      };
      if (unmuteBtn) {
        unmuteBtn.onclick = function (event) {
          event.stopPropagation();
          video.muted = false;
          hideUnmute();
          kick();
        };
      }
      video.onclick = function () {
        if (video.paused) {
          video.muted = false;
          hideUnmute();
          kick();
        } else {
          video.pause();
        }
      };
      video.addEventListener('play', function () {
        playedOnce = true;
        syncPlay();
      });
      video.addEventListener('pause', function () {
        if (playedOnce) syncPlay();
      });
      video.addEventListener('loadedmetadata', placeHud);
      window.addEventListener('resize', placeHud);
      boot();
    }
    var btn = document.getElementById('copyBtn');
    var code = document.getElementById('code');
    if (btn && code) {
      btn.onclick = function () {
        var text = (code.textContent || '').trim();
        function done() { btn.textContent = '已复制'; }
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done).catch(function () { fallback(text, done); });
        } else {
          fallback(text, done);
        }
      };
    }
    function fallback(text, done) {
      var input = document.createElement('textarea');
      input.value = text;
      input.setAttribute('readonly', '');
      input.style.position = 'fixed';
      input.style.left = '-9999px';
      document.body.appendChild(input);
      input.select();
      try { document.execCommand('copy'); done(); } catch (e) { prompt('复制邀请码', text); }
      document.body.removeChild(input);
    }
  </script>
</body>
</html>`
}
