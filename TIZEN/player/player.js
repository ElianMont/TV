/*
 * Reproductor HTML5 (<video>) para MP4 y HLS directos.
 * Tizen 5.0 reproduce HLS de forma nativa en <video>; no se usa hls.js ni se asume ningún códec:
 * si el televisor no puede decodificar la fuente se informa del error real del elemento.
 * Sin DRM. Los subtítulos solo se admiten como pistas <track> WebVTT (el SRT no es nativo).
 */
(function (w, d) {
  'use strict';
  var layer, v, bar, fill, time, hint, title, quality;
  var item = null, hideT = null, saveT = null, onClose = null;

  function $(id) { return d.getElementById(id); }
  function fmt(s) { s = Math.max(0, s | 0); var h = (s / 3600) | 0, m = ((s % 3600) / 60) | 0, x = s % 60;
    return (h ? h + ':' + ('0' + m).slice(-2) : m) + ':' + ('0' + x).slice(-2); }

  function say(t) { hint.textContent = t; hint.hidden = false; clearTimeout(hideT); hideT = setTimeout(function () { hint.hidden = true; }, 1400); }

  function update() {
    var dur = v.duration || 0;
    fill.style.width = dur ? (v.currentTime / dur * 100) + '%' : '0';
    time.textContent = fmt(v.currentTime) + ' / ' + fmt(dur);
    quality.textContent = v.videoHeight ? v.videoHeight + 'p' : '';
  }

  function save() {
    if (!item || !v.duration) return;
    w.Store.saveProgress(item, v.currentTime, v.duration);
  }

  function seek(delta) {
    v.currentTime = Math.min(Math.max(0, v.currentTime + delta), v.duration || 1e9);
    say((delta > 0 ? '+' : '') + delta + ' s');
  }

  function keys(name) {
    var step = w.Store.settings().seekStepSec;
    switch (name) {
      case 'PLAY': v.play(); break;
      case 'PAUSE': v.pause(); say('Pausa'); break;
      case 'PLAYPAUSE': case 'OK': if (v.paused) { v.play(); } else { v.pause(); say('Pausa'); } break;
      case 'STOP': case 'BACK': close(); break;
      case 'LEFT': seek(-step); break;
      case 'RIGHT': seek(step); break;
      case 'REW': seek(-step * 3); break;
      case 'FF': seek(step * 3); break;
      case 'UP': case 'DOWN': say(fmt(v.currentTime) + ' / ' + fmt(v.duration || 0)); break;
      default: return false;
    }
    return true;
  }

  function open(opts, closeCb) {
    layer = layer || $('playerLayer'); v = $('video'); bar = $('pBar'); fill = $('pFill'); time = $('pTime');
    hint = $('pHint'); title = $('pTitle'); quality = $('pQuality');
    item = opts; onClose = closeCb;
    title.textContent = opts.title || '';
    layer.hidden = false;
    while (v.firstChild) v.removeChild(v.firstChild);
    (opts.subtitles || []).forEach(function (s, i) {
      if (!/\.vtt(\?|$)/i.test(s)) return; // SRT no es nativo en Tizen 5.0
      var t = d.createElement('track'); t.kind = 'subtitles'; t.src = s; t.label = 'Subtítulos ' + (i + 1); v.appendChild(t);
    });
    v.onerror = function () {
      var e = v.error, codes = { 1: 'cancelada', 2: 'error de red', 3: 'no se pudo decodificar', 4: 'formato o fuente no compatible' };
      say('No se pudo reproducir: ' + (e ? (codes[e.code] || e.code) : 'error desconocido'));
    };
    v.ontimeupdate = update;
    v.onended = function () { save(); close(); };
    v.onloadedmetadata = function () {
      var p = w.Store.settings().autoResume && w.Store.getProgress(opts.url || opts.stream);
      if (p && p.pos > 5 && p.dur && p.pos < p.dur * 0.95) { v.currentTime = p.pos; say('Reanudado en ' + fmt(p.pos)); }
    };
    v.src = opts.stream;
    v.play();
    saveT = setInterval(save, 10000);
    w.Keys.push(keys);
  }

  function close() {
    save();
    clearInterval(saveT);
    w.Keys.pop();
    try { v.pause(); v.removeAttribute('src'); v.load(); } catch (e) {}
    layer.hidden = true;
    if (onClose) onClose();
  }

  w.Player = { open: open, close: close };
})(window, document);
