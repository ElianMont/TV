package tv.pelishd4k.remote

/** Nombres de tecla aceptados por el servidor (lista blanca). */
object RemoteKeys {
    val ALL: Set<String> = setOf(
        "UP", "DOWN", "LEFT", "RIGHT", "OK", "BACK", "HOME", "MENU",
        "PLAY_PAUSE", "PLAY", "PAUSE", "FF", "REW", "STOP",
        "PAGE_UP", "PAGE_DOWN", "ENTER", "BACKSPACE",
    )
}

/** Acciones que el servidor delega en la aplicación. Se invocan desde hilos de red. */
interface RemoteHandler {
    /** Pulsa una tecla de [RemoteKeys.ALL]. Devuelve false si no hay pantalla que la reciba. */
    fun key(name: String): Boolean
    fun move(dx: Float, dy: Float): Boolean
    fun tap(): Boolean
    fun scroll(dy: Float): Boolean
    fun text(text: String): Boolean

    /** Abre la búsqueda en el sitio. Devuelve un mensaje de error o null si todo fue bien. */
    fun search(query: String): String?

    /** Abre una URL dentro de la app (navegador o reproductor). Devuelve error o null. */
    fun open(url: String): String?

    /** action: up | down | mute. Devuelve el volumen 0..100 o null si no es posible. */
    fun volume(action: String): Int?
    fun seek(deltaMs: Long): Boolean
    fun state(): Map<String, Any?>
}
