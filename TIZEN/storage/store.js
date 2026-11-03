/* Datos locales por dispositivo (localStorage). `schema` y `deviceId` dejan preparada una sincronización futura. */
(function (w) {
  'use strict';
  var P = 'pelishd4k.';
  function read(k, d) { try { var v = w.localStorage.getItem(P + k); return v ? JSON.parse(v) : d; } catch (e) { return d; } }
  function write(k, v) { try { w.localStorage.setItem(P + k, JSON.stringify(v)); } catch (e) {} }

  var DEFAULTS = { homeUrl: 'https://pelishd4k.com', seekStepSec: 10, subtitleLang: '', audioLang: 'es', autoResume: true };

  function upsert(list, item, max) {
    var out = [item].concat(list.filter(function (x) { return x.url !== item.url; }));
    return out.slice(0, max);
  }

  w.Store = {
    schema: 1,
    deviceId: (function () {
      var id = read('deviceId', null);
      if (!id) { id = 'tz-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8); write('deviceId', id); }
      return id;
    })(),
    settings: function () { var s = read('settings', {}); var o = {}; for (var k in DEFAULTS) o[k] = (k in s) ? s[k] : DEFAULTS[k]; return o; },
    setSetting: function (k, v) { var s = read('settings', {}); s[k] = v; write('settings', s); },

    favorites: function () { return read('favorites', []); },
    isFavorite: function (url) { return this.favorites().some(function (x) { return x.url === url; }); },
    toggleFavorite: function (item) {
      var f = this.favorites(), i = -1;
      f.forEach(function (x, n) { if (x.url === item.url) i = n; });
      if (i >= 0) { f.splice(i, 1); write('favorites', f); return false; }
      write('favorites', upsert(f, { url: item.url, title: item.title, posterUrl: item.posterUrl || null }, 500)); return true;
    },

    history: function () { return read('history', []); },
    addHistory: function (item) {
      write('history', upsert(this.history(), { url: item.url, title: item.title, posterUrl: item.posterUrl || null, at: Date.now() }, 100));
    },
    clearHistory: function () { write('history', []); write('progress', {}); },

    /** Posición de reproducción por URL de la página. */
    saveProgress: function (item, pos, dur) {
      var p = read('progress', {});
      p[item.url] = { url: item.url, title: item.title, posterUrl: item.posterUrl || null, stream: item.stream || null, pos: pos, dur: dur, at: Date.now() };
      var keys = Object.keys(p).sort(function (a, b) { return p[b].at - p[a].at; }).slice(0, 100), o = {};
      keys.forEach(function (k) { o[k] = p[k]; });
      write('progress', o);
      write('last', o[item.url]);
    },
    getProgress: function (url) { return read('progress', {})[url] || null; },
    last: function () { return read('last', null); },
    continueWatching: function () {
      var p = read('progress', {});
      return Object.keys(p).map(function (k) { return p[k]; })
        .filter(function (x) { return x.pos > 5 && (!x.dur || x.pos < x.dur * 0.95); })
        .sort(function (a, b) { return b.at - a.at; });
    }
  };
})(window);
