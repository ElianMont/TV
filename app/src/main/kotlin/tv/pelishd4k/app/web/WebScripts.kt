package tv.pelishd4k.app.web

import java.util.Locale

/**
 * JavaScript inyectado en la página. Está escrito en ES5 a propósito: el WebView de Fire OS 6
 * y de algunos Android TV 9 es antiguo y no admite sintaxis moderna.
 *
 * Solo lee lo que la propia página ya expone en su DOM visible (título, og:image y los
 * elementos <video>/<source>/<track> del documento principal). No recorre iframes de otros
 * orígenes, no intercepta peticiones de red ni toca contenido protegido.
 */
object WebScripts {

    val PAGE_INFO = """
(function(){
  function abs(u){ try{ var a=document.createElement('a'); a.href=u; return a.href; }catch(e){ return ''; } }
  function meta(p){ var m=document.querySelector('meta[property="'+p+'"]')||document.querySelector('meta[name="'+p+'"]'); return m ? (m.getAttribute('content')||'') : ''; }
  var o={title:(meta('og:title')||document.title||'').replace(/^\s+|\s+${'$'}/g,'').slice(0,200), poster:'', hasPlayer:false, sources:[], tracks:[]};
  var img=meta('og:image')||meta('twitter:image'); if(img){ o.poster=abs(img); }
  var seen={};
  var vids=document.getElementsByTagName('video');
  for(var i=0;i<vids.length;i++){
    var v=vids[i]; o.hasPlayer=true;
    var list=[v.currentSrc, v.getAttribute('src')];
    var ss=v.getElementsByTagName('source');
    for(var j=0;j<ss.length;j++){ list.push(ss[j].getAttribute('src')); }
    for(var k=0;k<list.length;k++){
      if(!list[k]){ continue; }
      var u=abs(list[k]);
      if(u && /^https?:/i.test(u) && !seen[u]){ seen[u]=1; o.sources.push(u); }
    }
    var ts=v.getElementsByTagName('track');
    for(var t=0;t<ts.length;t++){ var s=ts[t].getAttribute('src'); if(s){ var tu=abs(s); if(/^https?:/i.test(tu)){ o.tracks.push(tu); } } }
  }
  if(!o.hasPlayer){
    var f=document.getElementsByTagName('iframe');
    for(var n=0;n<f.length;n++){
      var fs=(f[n].getAttribute('src')||'')+' '+(f[n].id||'')+' '+(f[n].className||'');
      if(/player|embed|video|stream/i.test(fs)){ o.hasPlayer=true; break; }
    }
  }
  return JSON.stringify(o);
})()
""".trimIndent()

    /** Pausa cualquier <video> del documento principal (antes de pasar al reproductor nativo). */
    const val PAUSE_MEDIA = "(function(){var v=document.getElementsByTagName('video');for(var i=0;i<v.length;i++){try{v[i].pause();}catch(e){}}})()"

    const val TOGGLE_MEDIA =
        "(function(){var v=document.getElementsByTagName('video');if(!v.length){return;}var m=v[0];if(m.paused){m.play();}else{m.pause();}})()"

    fun seekMedia(deltaSec: Int): String =
        "(function(){var v=document.getElementsByTagName('video');if(!v.length){return;}var m=v[0];" +
            "var t=m.currentTime+(" + deltaSec + ");if(t<0){t=0;}m.currentTime=t;})()"

    /** Escribe texto en el elemento enfocado (input, textarea o contenteditable) y avisa a la página. */
    fun insertText(text: String): String =
        "(function(t){var e=document.activeElement;if(!e){return 'none';}" +
            "function fire(el,n){var ev=document.createEvent('Event');ev.initEvent(n,true,true);el.dispatchEvent(ev);}" +
            "var tag=(e.tagName||'').toUpperCase();" +
            "if(tag==='INPUT'||tag==='TEXTAREA'){var v=e.value||'';var s=e.selectionStart;var en=e.selectionEnd;" +
            "if(typeof s==='number'){e.value=v.slice(0,s)+t+v.slice(en);var p=s+t.length;try{e.setSelectionRange(p,p);}catch(x){}}else{e.value=v+t;}" +
            "fire(e,'input');fire(e,'change');return 'ok';}" +
            "if(e.isContentEditable){document.execCommand('insertText',false,t);return 'ok';}" +
            "return 'none';})(" + jsString(text) + ")"

    fun jsString(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                ' ' -> append("\\u2028")
                ' ' -> append("\\u2029")
                else -> if (c < ' ') append(String.format(Locale.ROOT, "\\u%04x", c.code)) else append(c)
            }
        }
        append('"')
    }
}
