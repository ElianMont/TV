/* Mando Samsung. Códigos de Tizen TV; se registran las teclas multimedia (no llegan sin registrarlas). */
(function (w) {
  'use strict';
  var K = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, OK: 13, BACK: 10009, PLAY: 415, PAUSE: 19, PLAYPAUSE: 10252,
            STOP: 413, FF: 417, REW: 412, EXIT: 10182 };
  var names = {};
  Object.keys(K).forEach(function (n) { names[K[n]] = n; });

  function register() {
    try {
      if (w.tizen && w.tizen.tvinputdevice) {
        ['MediaPlay', 'MediaPause', 'MediaPlayPause', 'MediaStop', 'MediaFastForward', 'MediaRewind'].forEach(function (k) {
          try { w.tizen.tvinputdevice.registerKey(k); } catch (e) { /* tecla no disponible en este modelo */ }
        });
      }
    } catch (e) {}
  }

  var handlers = [];
  w.Keys = {
    CODES: K,
    register: register,
    /** El último manejador registrado recibe primero; devuelve true si consume la tecla. */
    push: function (fn) { handlers.push(fn); },
    pop: function () { handlers.pop(); }
  };
  w.addEventListener('keydown', function (e) {
    var name = names[e.keyCode];
    if (!name) return;
    var h = handlers[handlers.length - 1];
    if (h && h(name, e) !== false) e.preventDefault();
  });
})(window);
