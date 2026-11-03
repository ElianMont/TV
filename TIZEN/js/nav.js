/* Navegación espacial con flechas: salta al elemento .focusable más cercano en la dirección pulsada. */
(function (w, d) {
  'use strict';
  var cur = null;

  function items(scope) { return Array.prototype.slice.call((scope || d).querySelectorAll('.focusable')); }

  function setFocus(el) {
    if (!el) return;
    if (cur) cur.classList.remove('focus');
    cur = el;
    el.classList.add('focus');
    try { el.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (e) { el.scrollIntoView(); }
  }

  function move(dir, scope) {
    var list = items(scope);
    if (!list.length) return;
    if (!cur || list.indexOf(cur) < 0) { setFocus(list[0]); return; }
    var a = cur.getBoundingClientRect(), best = null, bestScore = Infinity;
    list.forEach(function (el) {
      if (el === cur) return;
      var b = el.getBoundingClientRect(), dx, dy, ok;
      var ax = a.left + a.width / 2, ay = a.top + a.height / 2, bx = b.left + b.width / 2, by = b.top + b.height / 2;
      dx = bx - ax; dy = by - ay;
      ok = (dir === 'LEFT' && dx < -4) || (dir === 'RIGHT' && dx > 4) || (dir === 'UP' && dy < -4) || (dir === 'DOWN' && dy > 4);
      if (!ok) return;
      var main = (dir === 'LEFT' || dir === 'RIGHT') ? Math.abs(dx) : Math.abs(dy);
      var cross = (dir === 'LEFT' || dir === 'RIGHT') ? Math.abs(dy) : Math.abs(dx);
      var score = main + cross * 3; // prioriza mantener la fila/columna
      if (score < bestScore) { bestScore = score; best = el; }
    });
    if (best) setFocus(best);
  }

  w.Nav = {
    focus: setFocus,
    move: move,
    current: function () { return cur; },
    first: function (scope) { var l = items(scope); if (l.length) setFocus(l[0]); },
    reset: function () { cur = null; }
  };
})(window, document);
