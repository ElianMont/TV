# Media3 instancia HLS/DASH por reflexión desde DefaultMediaSourceFactory.
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory { *; }
-dontwarn androidx.media3.**

# NanoHTTPD usa reflexión mínima y clases internas.
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Room (las clases generadas ya llevan sus reglas; esto cubre las entidades).
-keep class tv.pelishd4k.app.data.** { *; }

# Interfaz JS expuesta al WebView (no se usa addJavascriptInterface, por seguridad).
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
