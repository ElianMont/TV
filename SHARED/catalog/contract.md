# Contrato de datos del catálogo

Forma común que cada plataforma debe producir al leer pelishd4k.com. Ningún campo se inventa:
si el sitio no lo ofrece, queda `null`.

```
Item   { url, title, kind: "movie"|"series", posterUrl|null, year|null,
         description|null, genres: string[] }
Series { ...Item, seasons: [{ number, episodes: [{ number, title|null, url }] }] }
Source { url, kind: "mp4"|"hls"|"dash"|"embed", label|null }
```

- `url` es siempre la página del sitio; es la clave de favoritos/historial/progreso.
- Un `Source` de tipo `embed` (iframe de terceros) no es reproducible con el reproductor propio:
  se muestra la página del sitio y se documenta.
- **Pendiente:** los selectores concretos dependen del HTML real de pelishd4k.com
  (no accesible desde el entorno de desarrollo). Se implementan en un único adaptador
  (`TIZEN/services/siteAdapter.js`) para cambiarlos sin tocar la interfaz.
