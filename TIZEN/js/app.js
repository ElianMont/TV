/* Pantallas de la app Samsung. Todo el texto de origen externo se inserta con textContent (nunca innerHTML). */
(function (w, d) {
  'use strict';
  var TABS = [
    ['home', 'Inicio'], ['movies', 'Películas'], ['series', 'Series'], ['trending', 'Tendencias'],
    ['favorites', 'Favoritos'], ['continue', 'Continuar viendo'], ['search', 'Buscador'], ['settings', 'Configuración']
  ];
  var side = d.getElementById('side'), content = d.getElementById('content'), toastEl = d.getElementById('toast');
  var tab = 'home', area = 'side', toastT = null, query = '';

  function el(tag, cls, text) { var e = d.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
  function clear(n) { while (n.firstChild) n.removeChild(n.firstChild); }
  function toast(t) { toastEl.textContent = t; toastEl.hidden = false; clearTimeout(toastT); toastT = setTimeout(function () { toastEl.hidden = true; }, 2800); }

  function card(item, onOk) {
    var c = el('div', 'card focusable');
    var p = el('div', 'poster');
    if (item.posterUrl && /^https:/i.test(item.posterUrl)) p.style.backgroundImage = 'url("' + item.posterUrl.replace(/["\\\n]/g, '') + '")';
    c.appendChild(p); c.appendChild(el('div', 't', item.title));
    c.onOk = onOk; c.item = item; return c;
  }

  function row(title, items, onOk) {
    var s = el('section'); s.appendChild(el('h2', null, title));
    var r = el('div', 'row'); items.forEach(function (i) { r.appendChild(card(i, onOk)); }); s.appendChild(r); return s;
  }

  function message(text) { return el('div', 'empty', text); }

  function openItem(item) {
    // Con fuente directa guardada se reanuda en el reproductor; si no, se necesita el adaptador del sitio.
    if (item.stream) return play(item);
    if (!w.Site.configured) return toast(w.Site.notConfiguredMessage);
    w.Site.sources(item.url).then(function (src) {
      var direct = src.filter(function (s) { return s.kind === 'mp4' || s.kind === 'hls'; })[0];
      if (!direct) return toast('Esta página solo ofrece un reproductor externo, no reproducible aquí.');
      play({ url: item.url, title: item.title, posterUrl: item.posterUrl, stream: direct.url });
    }).catch(function (e) { toast(e.message); });
  }

  function play(item) {
    w.Store.addHistory(item);
    area = 'player';
    w.Player.open(item, function () { area = 'content'; render(); });
  }

  function render() {
    clear(side); clear(content);
    side.appendChild(el('h1', null, 'PelisHD4K TV'));
    TABS.forEach(function (t) {
      var e = el('div', 'tab focusable' + (t[0] === tab ? ' sel' : ''), t[1]); e.tabId = t[0]; side.appendChild(e);
    });
    var s = w.Store;
    if (tab === 'home') {
      var hero = el('div', 'hero'); hero.appendChild(el('b', null, 'PelisHD4K TV'));
      hero.appendChild(el('div', 'empty', 'Cine en tu pantalla grande')); content.appendChild(hero);
      var cont = s.continueWatching(); if (cont.length) content.appendChild(row('Continuar viendo', cont, openItem));
      var fav = s.favorites(); if (fav.length) content.appendChild(row('Favoritos', fav, openItem));
      var hist = s.history(); if (hist.length) content.appendChild(row('Historial', hist, openItem));
      if (!w.Site.configured) content.appendChild(message(w.Site.notConfiguredMessage));
    } else if (tab === 'favorites') {
      content.appendChild(s.favorites().length ? row('Favoritos', s.favorites(), openItem) : message('Aún no tienes favoritos.'));
    } else if (tab === 'continue') {
      content.appendChild(s.continueWatching().length ? row('Continuar viendo', s.continueWatching(), openItem) : message('Nada por continuar todavía.'));
    } else if (tab === 'settings') {
      content.appendChild(el('h2', null, 'Configuración'));
      var st = s.settings();
      [['Salto de avance/retroceso: ' + st.seekStepSec + ' s', function () { var o = [5, 10, 15, 30]; s.setSetting('seekStepSec', o[(o.indexOf(st.seekStepSec) + 1) % o.length]); }],
       ['Reanudar donde lo dejaste: ' + (st.autoResume ? 'Sí' : 'No'), function () { s.setSetting('autoResume', !st.autoResume); }],
       ['Borrar historial y progreso', function () { s.clearHistory(); toast('Historial borrado'); }],
       ['Dispositivo: ' + s.deviceId, null]
      ].forEach(function (r) { var b = el('div', 'btn' + (r[1] ? ' focusable' : ''), r[0]); b.onOk = r[1] && function () { r[1](); render(); focusContent(); }; b.style.display = 'block'; b.style.marginBottom = '12px'; content.appendChild(b); });
    } else {
      // Películas, Series, Tendencias, Buscador: dependen del adaptador del sitio.
      content.appendChild(el('h2', null, (TABS.filter(function (t) { return t[0] === tab; })[0] || [0, ''])[1]));
      content.appendChild(message(w.Site.configured ? '' : w.Site.notConfiguredMessage));
    }
    var sel = side.querySelector('.sel'); if (area === 'side' && sel) w.Nav.focus(sel);
  }

  function focusContent() { var f = content.querySelector('.focusable'); if (f) { area = 'content'; w.Nav.focus(f); } }

  w.Keys.register();
  w.Keys.push(function (name) {
    var cur = w.Nav.current();
    if (name === 'UP' || name === 'DOWN' || name === 'LEFT' || name === 'RIGHT') {
      if (area === 'content' && name === 'LEFT') {
        // Solo sale a la barra lateral si no hay nada más a la izquierda.
        var before = cur; w.Nav.move('LEFT', content);
        if (w.Nav.current() === before) { area = 'side'; var sel = side.querySelector('.sel'); if (sel) w.Nav.focus(sel); }
      } else if (area === 'side') {
        if (name === 'RIGHT') focusContent(); else w.Nav.move(name, side);
      } else { w.Nav.move(name, content); }
      return true;
    }
    if (name === 'OK') {
      if (!cur) return true;
      if (cur.tabId) { tab = cur.tabId; render(); var s2 = side.querySelector('.sel'); if (s2) w.Nav.focus(s2); }
      else if (cur.onOk) cur.onOk(cur.item);
      return true;
    }
    if (name === 'BACK') {
      if (area === 'content') { area = 'side'; var s3 = side.querySelector('.sel'); if (s3) w.Nav.focus(s3); }
      else if (tab !== 'home') { tab = 'home'; render(); }
      else { try { w.tizen.application.getCurrentApplication().exit(); } catch (e) {} }
      return true;
    }
    return false;
  });

  render();
})(window, document);
