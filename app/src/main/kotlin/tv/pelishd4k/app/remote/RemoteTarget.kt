package tv.pelishd4k.app.remote

/** Pantalla que puede recibir órdenes del control remoto del iPhone. Se invoca en el hilo principal. */
interface RemoteTarget {
    fun remoteKey(code: Int)
    fun remoteBack()
    fun remoteHome()
    fun remoteMove(dx: Float, dy: Float)
    fun remoteTap()
    fun remoteScroll(dy: Float)
    fun remoteText(text: String): Boolean
    fun remoteSeek(deltaMs: Long): Boolean
    fun remoteState(): Map<String, Any?>
}
