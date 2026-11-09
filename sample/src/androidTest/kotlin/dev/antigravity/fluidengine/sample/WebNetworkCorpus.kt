package dev.antigravity.fluidengine.sample

/** Cosa deve fare la prova dopo la prima pittura perche' il canale si attivi. */
internal enum class WebGesture { None, Tap, LongPress }

/** Un modo in cui un documento prova a far uscire traffico dal contenitore. */
internal class WebNetworkChannel(val name: String, val gesture: WebGesture = WebGesture.None, val html: (WebTarget) -> String)

/** Destinazione delle prove: un socket locale in ascolto, scritto nei tre modi che un documento usa. */
internal class WebTarget(val https: String, val http: String, val authority: String)

private const val Head = "<meta name='viewport' content='width=device-width'><title>SAFE</title>"
private const val Tall = "style='display:block;height:120px'"

private fun channel(name: String, gesture: WebGesture = WebGesture.None, body: (WebTarget) -> String) =
  WebNetworkChannel(name, gesture) { Head + body(it) + "<p>Canale: $name</p>" }

/** Censimento dei canali: ogni voce e' un documento minimo che usa un solo meccanismo.
 *
 * Tutti devono dare zero connessioni native. La colonna «prima» del censimento nasce dalle stesse voci
 * eseguite sulla 3.1.0.
 */
internal fun webNetworkCensus(): List<WebNetworkChannel> = listOf(
  channel("baseline") { "" },
  // Suggerimenti: non sono caricamenti, quindi non passano da shouldInterceptRequest.
  channel("link_preconnect") { "<link rel='preconnect' href='${it.https}'>" },
  channel("link_preconnect_http") { "<link rel='preconnect' href='${it.http}'>" },
  channel("link_preconnect_crossorigin") { "<link rel='preconnect' href='${it.https}' crossorigin>" },
  channel("link_preconnect_in_body") { "<body><p>Testo</p><link rel='preconnect' href='${it.https}'>" },
  channel("link_dns_prefetch") { "<link rel='dns-prefetch' href='//${it.authority}'>" },
  channel("link_preconnect_dns_prefetch") { "<link rel='dns-prefetch preconnect' href='${it.https}'>" },
  // Caricamenti anticipati: sono richieste vere.
  channel("link_preload_image") { "<link rel='preload' as='image' href='${it.https}/preload.png'>" },
  channel("link_preload_style") { "<link rel='preload' as='style' href='${it.https}/preload.css'>" },
  channel("link_preload_font") { "<link rel='preload' as='font' type='font/woff2' crossorigin href='${it.https}/preload.woff2'>" },
  channel("link_preload_fetch") { "<link rel='preload' as='fetch' crossorigin href='${it.https}/preload.json'>" },
  channel("link_preload_imagesrcset") { "<link rel='preload' as='image' imagesrcset='${it.https}/a.png 1x, ${it.https}/b.png 2x'>" },
  channel("link_modulepreload") { "<link rel='modulepreload' href='${it.https}/module.js'>" },
  channel("link_prefetch") { "<link rel='prefetch' href='${it.https}/prefetch'>" },
  channel("link_prerender") { "<link rel='prerender' href='${it.https}/prerender'>" },
  channel("link_icon") { "<link rel='icon' href='${it.https}/icon.png'><link rel='shortcut icon' href='${it.https}/favicon.ico'>" },
  channel("link_apple_touch_icon") { "<link rel='apple-touch-icon' href='${it.https}/touch.png'>" },
  channel("link_manifest") { "<link rel='manifest' href='${it.https}/manifest.json'>" },
  channel("link_stylesheet") { "<link rel='stylesheet' href='${it.https}/style.css'>" },
  channel("link_alternate_stylesheet") { "<link rel='alternate stylesheet' title='b' href='${it.https}/alternate.css'>" },
  channel("link_search_alternate_canonical") {
    "<link rel='search' type='application/opensearchdescription+xml' href='${it.https}/search.xml'>" +
      "<link rel='alternate' type='application/rss+xml' href='${it.https}/feed'><link rel='canonical' href='${it.https}/canonical'>" +
      "<link rel='amphtml' href='${it.https}/amp'><link rel='pingback' href='${it.https}/pingback'>"
  },
  // Equivalenti in <meta>.
  channel("meta_refresh") { "<meta http-equiv='refresh' content='0;url=${it.https}/refresh'>" },
  channel("meta_http_equiv_link") { "<meta http-equiv='Link' content='<${it.https}>; rel=preconnect'>" },
  channel("meta_dns_prefetch_control_anchor") {
    "<meta http-equiv='x-dns-prefetch-control' content='on'><a href='${it.https}/a'>a</a><a href='${it.http}/b'>b</a>"
  },
  channel("meta_csp_report") {
    "<meta http-equiv='Content-Security-Policy' content=\"img-src 'none'; report-uri ${it.https}/report\"><img src='${it.https}/violation.png'>"
  },
  channel("meta_origin_trial_accept_ch") {
    "<meta http-equiv='origin-trial' content='AAAA'><meta http-equiv='accept-ch' content='sec-ch-ua-model'>" +
      "<meta http-equiv='delegate-ch' content='sec-ch-ua-model ${it.https}'><img src='${it.https}/hints.png'>"
  },
  // Script e regole di speculazione: JavaScript e' spento e la CSP li vieta.
  channel("speculation_rules") {
    "<script type='speculationrules'>{\"prefetch\":[{\"source\":\"list\",\"urls\":[\"${it.https}/spec-prefetch\"],\"eagerness\":\"immediate\"}]," +
      "\"prerender\":[{\"source\":\"list\",\"urls\":[\"${it.https}/spec-prerender\"],\"eagerness\":\"immediate\"}]}</script>" +
      "<a href='${it.https}/spec-prefetch'>a</a>"
  },
  channel("script") {
    "<script src='${it.https}/script.js'></script><script>fetch('${it.https}/fetch');navigator.sendBeacon('${it.https}/beacon');" +
      "new WebSocket('wss://${it.authority}/ws');new Image().src='${it.https}/image'</script>"
  },
  // Risorse ordinarie: devono arrivare a onRequest e fermarsi li'.
  channel("img") { "<img src='${it.https}/pixel.png' width='1' height='1'><img src='${it.http}/pixel.png'>" },
  channel("img_srcset_picture") {
    "<img srcset='${it.https}/a.png 1x, ${it.https}/b.png 2x'><picture><source srcset='${it.https}/c.webp' type='image/webp'><img src='${it.https}/d.png'></picture>"
  },
  channel("img_lazy_below_fold") { "<div style='height:6000px'></div><img loading='lazy' src='${it.https}/lazy.png'>" },
  channel("input_image_body_background") {
    "<body background='${it.https}/body.png'><input type='image' src='${it.https}/input.png'><table background='${it.https}/table.png'><tr><td background='${it.https}/td.png'>x</td></tr></table>"
  },
  channel("css_urls") {
    "<style>@import url('${it.https}/import.css');@font-face{font-family:remote;src:url('${it.https}/font.woff2')}" +
      "p{background:url('${it.https}/bg.png');font-family:remote;cursor:url('${it.https}/cursor.png'),auto;list-style-image:url('${it.https}/list.png');" +
      "border-image:url('${it.https}/border.png') 1;mask-image:url('${it.https}/mask.png');filter:url('${it.https}/filter.svg#f')}" +
      "p::after{content:url('${it.https}/content.png')}</style><p style=\"background-image:image-set('${it.https}/set.png' 1x)\">Testo</p>"
  },
  channel("svg_external") {
    "<svg width='20' height='20'><image href='${it.https}/image.png' width='10' height='10'/><use href='${it.https}/use.svg#a'/>" +
      "<filter id='f'><feImage href='${it.https}/fe.png'/></filter><rect width='5' height='5' filter='url(#f)'/></svg>"
  },
  channel("img_data_svg_with_hint") {
    val svg = "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'><link xmlns='http://www.w3.org/1999/xhtml' rel='preconnect' href='${it.https}'/>" +
      "<image href='${it.https}/inner.png' width='5' height='5'/></svg>"
    "<img src=\"data:image/svg+xml,${svg.replace("<", "%3C").replace(">", "%3E").replace("#", "%23")}\">"
  },
  channel("video_audio") {
    "<video src='${it.https}/video.mp4' poster='${it.https}/poster.png' autoplay muted playsinline preload='auto'></video>" +
      "<audio src='${it.https}/audio.mp3' autoplay preload='auto'></audio><video><track src='${it.https}/track.vtt' default></video>"
  },
  channel("video_hls") {
    "<video src='${it.https}/stream.m3u8' autoplay muted playsinline preload='auto'></video>" +
      "<video autoplay muted><source src='${it.http}/other.m3u8' type='application/vnd.apple.mpegurl'></video>"
  },
  channel("object_embed") {
    "<object data='${it.https}/object'></object><embed src='${it.https}/embed'><object data='${it.https}/object.svg' type='image/svg+xml'></object>"
  },
  channel("iframe_src") { "<iframe src='${it.https}/frame'></iframe><iframe src=\"data:text/html,<link rel=preconnect href=${it.https}>\"></iframe>" },
  channel("iframe_srcdoc_hint") { "<iframe srcdoc=\"<link rel='preconnect' href='${it.https}'>\"></iframe>" },
  channel("iframe_srcdoc_hint_as_entities") { "<iframe srcdoc=\"&lt;link rel=preconnect href=${it.https}&gt;\"></iframe>" },
  channel("iframe_srcdoc_image") { "<iframe srcdoc=\"<img src='${it.https}/inner.png'>\"></iframe>" },
  channel("frameset") { "<frameset cols='*,*'><frame src='${it.https}/frame'><frame src='about:blank'></frameset>" },
  // Documenti annidati con un indirizzo `data:`: il suggerimento e' codificato nell'indirizzo, dove il sigillo non lo
  // vede, e li ferma solo la CSP (frame-src, object-src).
  channel("frame_data_url_with_hint") { "<frameset cols='*,*'><frame src=\"data:text/html,%3Clink%20rel=preconnect%20href=${it.https}%3E\"><frame src='about:blank'></frameset>" },
  channel("object_embed_data_url_with_hint") {
    "<object data=\"data:text/html,%3Clink%20rel=preconnect%20href=${it.https}%3E\"></object>" +
      "<embed type='text/html' src=\"data:text/html,%3Clink%20rel=preconnect%20href=${it.https}%3E\">"
  },
  channel("base_href") { "<base href='${it.https}/base/'><img src='relative.png'><a href='relative'>x</a>" },
  // Gesti: quello che parte toccando.
  channel("anchor_tap", WebGesture.Tap) { "<a $Tall href='${it.https}/destination'>https://display.invalid</a>" },
  channel("anchor_ping_tap", WebGesture.Tap) { "<a $Tall href='${it.https}/destination' ping='${it.https}/ping ${it.http}/ping'>ping</a>" },
  channel("anchor_target_blank_tap", WebGesture.Tap) { "<a $Tall target='_blank' rel='noopener' href='${it.https}/new-window'>finestra</a>" },
  channel("anchor_download_tap", WebGesture.Tap) { "<a $Tall download href='${it.https}/download.bin'>scarica</a>" },
  channel("anchor_long_press", WebGesture.LongPress) { "<a $Tall href='${it.https}/destination'>pressione lunga</a>" },
  channel("image_long_press", WebGesture.LongPress) { "<img $Tall src='${it.https}/long.png' alt='immagine'>" },
  channel("form_submit_tap", WebGesture.Tap) {
    "<form action='${it.https}/form' method='post'><input name='secret' value='private' type='hidden'><button style='display:block;width:100%;height:120px'>Invia</button></form>"
  },
  channel("form_get_submit_tap", WebGesture.Tap) {
    "<form action='${it.https}/form'><input type='submit' style='display:block;width:100%;height:120px' value='Invia'></form>"
  },
  channel("image_map_tap", WebGesture.Tap) {
    "<img $Tall width='600' usemap='#m' src='cid:local'><map name='m'><area shape='rect' coords='0,0,600,120' href='${it.https}/area' ping='${it.https}/area-ping'></map>"
  },
)

/** Varianti travestite di un suggerimento: la destinazione e' sempre la stessa, cambia come e' scritto il tag.
 *
 * Le voci `assumption_` non sono elementi `link` per la specifica HTML e il sigillo non le tocca: stanno qui
 * per provare, col vero Chromium, il fatto su cui il sigillo si appoggia (solo la sequenza letterale crea
 * l'elemento).
 */
internal fun webNetworkDisguises(): List<WebNetworkChannel> {
  fun hint(name: String, body: (String) -> String) = WebNetworkChannel(name) { "<title>SAFE</title>" + body(it.https) + "<p>Variante: $name</p>" }
  return listOf(
    hint("mixed_case") { "<LiNk ReL=PrEcOnNeCt HrEf=$it>" },
    hint("slash_separators") { "<link/rel=preconnect/href=$it>" },
    hint("tab_separators") { "<link\trel=preconnect\thref=$it>" },
    hint("form_feed_separators") { "<link\u000Crel=preconnect\u000Chref=$it>" },
    hint("newline_separators") { "<link\nrel=preconnect\r\nhref=$it\n>" },
    hint("carriage_return_separator") { "<link\rrel=preconnect\rhref=$it>" },
    hint("entity_decimal_in_rel") { "<link rel=\"&#112;reconnect\" href=\"$it\">" },
    hint("entity_hex_in_rel") { "<link rel=\"&#x70;reconnect\" href=\"$it\">" },
    hint("entity_named_in_href") { "<link rel=preconnect href=\"https&colon;//${it.removePrefix("https://")}\">" },
    hint("entity_without_semicolon") { "<link rel=\"pre&#99onnect\" href=\"$it\">" },
    hint("duplicate_rel_hint_last") { "<link rel=stylesheet rel=preconnect href=$it>" },
    hint("duplicate_rel_hint_first") { "<link rel=preconnect rel=stylesheet href=$it>" },
    hint("duplicate_href") { "<link rel=preconnect href='https://other.invalid' href=$it>" },
    hint("several_words_in_rel") { "<link rel=\"stylesheet preconnect\" href=\"$it\">" },
    hint("several_words_and_spaces") { "<link rel=\" \tdns-prefetch\npreconnect \" href=\"$it\">" },
    hint("unclosed_tag_at_end") { "<p>prima</p><link rel=preconnect href=$it" },
    hint("unclosed_quote_at_end") { "<p>prima</p><link rel=preconnect href=$it x=\"" },
    hint("nul_after_name") { "<link\u0000 rel=preconnect href=$it><link \u0000rel=preconnect href=$it>" },
    hint("nul_inside_attribute") { "<link rel=pre\u0000connect href=$it><link rel=preconnect x=\"\u0000\" href=$it>" },
    hint("missing_space_after_quote") { "<link x=\"y\"rel=preconnect href=$it>" },
    hint("equals_before_name") { "<link =x rel=preconnect href=$it>" },
    hint("self_closing") { "<link rel=preconnect href=$it />" },
    hint("inside_svg") { "<svg><link rel=preconnect href=$it></svg>" },
    hint("inside_math") { "<math><mi><link rel=preconnect href=$it></mi></math>" },
    hint("inside_svg_title") { "<svg><title><link rel=preconnect href=$it></title></svg>" },
    hint("inside_svg_foreign_object") { "<svg><foreignObject><link rel=preconnect href=$it></foreignObject></svg>" },
    hint("inside_svg_style") { "<svg><style><link rel=preconnect href=$it></style></svg>" },
    hint("after_unclosed_comment") { "<p>prima</p><!-- <link rel=preconnect href=$it>" },
    hint("after_abrupt_comment") { "<!--><link rel=preconnect href=$it>" },
    hint("after_bang_comment") { "<!--!><link rel=preconnect href=$it> -->" },
    hint("after_nested_comment") { "<!-- <!-- --><link rel=preconnect href=$it> -->" },
    hint("after_cdata_in_html") { "<![CDATA[ ]]><link rel=preconnect href=$it>" },
    hint("after_unclosed_cdata_in_svg") { "<svg><![CDATA[ <link rel=preconnect href=$it>" },
    hint("after_processing_instruction") { "<?xml version='1.0'?><?xml-stylesheet href='$it/x.xsl'?><link rel=preconnect href=$it>" },
    hint("in_body_after_text") { "<body><p>Testo</p><div><link rel=preconnect href=$it></div>" },
    hint("inside_table") { "<table><tr><link rel=preconnect href=$it><td>x</td></tr></table>" },
    hint("inside_select") { "<select><option>a<link rel=preconnect href=$it></select>" },
    hint("inside_template") { "<template><link rel=preconnect href=$it></template>" },
    hint("inside_declarative_shadow_root") { "<div><template shadowrootmode='open'><link rel=preconnect href=$it></template></div>" },
    hint("inside_noscript_in_head") { "<head><noscript><link rel=preconnect href=$it></noscript></head>" },
    hint("inside_noscript_in_body") { "<body><noscript><link rel=preconnect href=$it></noscript>" },
    hint("inside_title") { "<title><link rel=preconnect href=$it></title>" },
    hint("inside_textarea") { "<textarea><link rel=preconnect href=$it></textarea>" },
    hint("inside_style") { "<style><link rel=preconnect href=$it></style>" },
    hint("inside_xmp_and_plaintext") { "<xmp><link rel=preconnect href=$it></xmp><plaintext><link rel=preconnect href=$it>" },
    hint("inside_attribute_value") { "<p title=\"<link rel=preconnect href=$it>\">x</p><p title='><link rel=preconnect href=$it>'>y</p>" },
    hint("double_angle") { "<<link rel=preconnect href=$it><link<link rel=preconnect href=$it>" },
    hint("after_end_tag_with_attributes") { "</link rel=preconnect href=$it><link rel=preconnect href=$it>" },
    hint("after_byte_order_mark") { "﻿<link rel=preconnect href=$it>" },
    hint("after_doctype_quirks") { "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 3.2//EN\"><link rel=preconnect href=$it>" },
    hint("after_large_padding") { "<p>" + "Riempimento. ".repeat(12_000) + "</p><link rel=preconnect href=$it>" },
    hint("utf7_charset") { "<meta charset='utf-7'>+ADw-link rel+AD0-preconnect href+AD0-$it+AD4-" },
    hint("stylesheet_with_hint_in_same_tag") { "<link rel=stylesheet href=$it/a.css rel=preconnect>" },
    hint("iframe_srcdoc_plain") { "<iframe srcdoc=\"<link rel=preconnect href=$it>\"></iframe>" },
    hint("iframe_srcdoc_entities") { "<iframe srcdoc=\"&lt;link rel=preconnect href=$it&gt;\"></iframe>" },
    hint("iframe_srcdoc_numeric_entities") { "<IFRAME/SRCDOC='&#60;link rel=preconnect href=$it&#62;'></IFRAME>" },
    hint("iframe_srcdoc_nested_twice") {
      "<iframe srcdoc=\"&lt;iframe srcdoc=&quot;&amp;lt;link rel=preconnect href=$it&amp;gt;&quot;&gt;&lt;/iframe&gt;\"></iframe>"
    },
    hint("iframe_unclosed_at_end") { "<iframe srcdoc=\"&lt;link rel=preconnect href=$it&gt;\"" },
    hint("iframe_inside_svg") { "<svg><foreignObject><iframe srcdoc=\"&lt;link rel=preconnect href=$it&gt;\"></iframe></foreignObject></svg>" },
    hint("meta_dns_control_mixed_case") { "<META HTTP-EQUIV=X-DNS-Prefetch-Control CONTENT=on><a href='$it/a'>a</a>" },
    hint("meta_dns_control_slashes") { "<meta/http-equiv=x-dns-prefetch-control/content=on><a href='$it/a'>a</a>" },
    hint("meta_dns_control_entities") { "<meta http-equiv=\"x-dns-prefetch-&#99;ontrol\" content=\"&#111;n\"><a href='$it/a'>a</a>" },
    hint("meta_dns_control_in_body") { "<body><p>x</p><meta http-equiv='x-dns-prefetch-control' content='on'><a href='$it/a'>a</a>" },
    hint("meta_refresh_with_viewport_name") { "<meta name='viewport' http-equiv='refresh' content='0;url=$it/refresh'>" },
    // Non sono elementi link: il sigillo non li riscrive, e Chromium non deve trattarli come tali.
    hint("assumption_nul_inside_name") { "<l\u0000ink rel=preconnect href=$it><\u0000link rel=preconnect href=$it><lin\u0000k rel=preconnect href=$it>" },
    hint("assumption_invisible_inside_name") { "<l﻿ink rel=preconnect href=$it><li­nk rel=preconnect href=$it><l​ink rel=preconnect href=$it>" },
    hint("assumption_unicode_case_folding") { "<linK rel=preconnect href=$it><lınk rel=preconnect href=$it><LİNK rel=preconnect href=$it>" },
    hint("assumption_space_after_angle") { "< link rel=preconnect href=$it><\tlink rel=preconnect href=$it><\nlink rel=preconnect href=$it>" },
    hint("assumption_non_html_space_after_name") { "<link rel=preconnect href=$it><link\u000Brel=preconnect href=$it><link rel=preconnect href=$it>" },
    hint("assumption_entity_angle") { "&lt;link rel=preconnect href=$it&gt;&#60;link rel=preconnect href=$it&#62;&#x3c;link rel=preconnect href=$it>" },
    hint("assumption_fullwidth_angle") { "＜link rel=preconnect href=$it＞" },
  )
}

/** Canali che lasciano traccia solo nelle richieste DNS: il nome e' diverso a ogni prova, per evitare la cache. */
internal fun webDnsChannels(): List<Pair<String, (String) -> String>> = listOf(
  "baseline_without_names" to { _: String -> "<title>SAFE</title><p>Nessun nome nel documento</p>" },
  "link_dns_prefetch" to { host: String -> "<title>SAFE</title><link rel='dns-prefetch' href='//$host'>" },
  "link_preconnect_by_name" to { host: String -> "<title>SAFE</title><link rel='preconnect' href='https://$host'>" },
  "meta_control_on_with_anchors" to { host: String ->
    "<title>SAFE</title><meta http-equiv='x-dns-prefetch-control' content='on'><a href='https://$host/a'>a</a><a href='http://b.$host/b'>b</a>"
  },
  "anchors_without_control" to { host: String -> "<title>SAFE</title><a href='https://$host/a'>a</a><a href='http://b.$host/b'>b</a>" },
  "disguised_hints" to { host: String ->
    "<title>SAFE</title><LiNk/ReL=dns-prefetch/HrEf=//a.$host><svg><link rel=dns-prefetch href=//b.$host></svg>" +
      "<link rel=stylesheet rel=dns-prefetch href=//c.$host><iframe srcdoc=\"&lt;link rel=dns-prefetch href=//d.$host&gt;\"></iframe>" +
      "<meta/http-equiv=x-dns-prefetch-control/content=on><a href='https://e.$host/'>e</a>"
  },
  "blocked_resources_by_name" to { host: String ->
    "<title>SAFE</title><img src='https://$host/pixel.png'><style>@import url('https://css.$host/a.css');</style>" +
      "<link rel='stylesheet' href='https://style.$host/a.css'><video src='https://media.$host/v.mp4' autoplay muted></video>"
  },
)
