/*
 * Adaptador de pelishd4k.com. Es el ÚNICO lugar que conoce la estructura del sitio.
 *
 * ESTADO: sin configurar. No se pudo acceder al HTML del sitio durante el desarrollo, y no se
 * inventan selectores ni datos. Mientras `configured` sea false, la interfaz lo indica en
 * pantalla en lugar de mostrar contenido falso. Contrato de salida: SHARED/catalog/contract.md
 */
(function (w) {
  'use strict';
  var NOT_CONFIGURED = 'El catálogo aún no está conectado: falta analizar la estructura de pelishd4k.com.';

  function fail() { return Promise.reject(new Error(NOT_CONFIGURED)); }

  w.Site = {
    configured: false,
    notConfiguredMessage: NOT_CONFIGURED,
    /** section: "home" | "movies" | "series" | "trending" -> Promise<Item[]> */
    list: fail,
    /** url -> Promise<Item|Series> con descripción, géneros y temporadas */
    detail: fail,
    /** query -> Promise<Item[]> */
    search: fail,
    /** url de la página -> Promise<Source[]> (solo lo que la página expone de forma visible) */
    sources: fail
  };
})(window);
