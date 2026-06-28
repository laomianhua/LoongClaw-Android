package com.littlehelper.shell.modules

import android.util.Log
import android.webkit.WebView

/**
 * Gateway Canvas 地图页加载完成后注入高德跳转逻辑（无 UI 按钮）。
 * 会移除 Gateway / 历史版本遗留的大按钮；打开高德由白板底栏触发 [CanvasWebViewBridge.openAmap]。
 */
object AmapCanvasInjector {

    private const val TAG = "AmapCanvasInjector"

    private val EXCLUDED_CANVAS = setOf(
        "webview_spec_test.html",
        "blank.html",
        "test.html",
        "canvas_blank.html",
        "canvas_test.html"
    )

    fun shouldInject(pageUrl: String?): Boolean {
        val path = canvasPathFromUrl(pageUrl) ?: return false
        if (!path.contains("/__openclaw__/canvas/") || !path.endsWith(".html")) return false
        val fileName = path.substringAfterLast('/')
        if (fileName in EXCLUDED_CANVAS) return false
        if (fileName.matches(Regex("t\\d+_.*\\.html"))) return false
        return true
    }

    internal fun canvasPathFromUrl(pageUrl: String?): String? {
        val trimmed = pageUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
        val schemeIdx = withoutQuery.indexOf("://")
        val path = if (schemeIdx >= 0) {
            withoutQuery.substring(schemeIdx + 3).substringAfter('/', "")
        } else {
            withoutQuery.trimStart('/')
        }
        return if (path.startsWith("/")) path else "/$path"
    }

    fun injectIfNeeded(webView: WebView, pageUrl: String?) {
        if (!shouldInject(pageUrl)) return
        Log.d(TAG, "Injecting Amap bridge for $pageUrl")
        webView.evaluateJavascript(INJECT_SCRIPT, null)
    }

    private val INJECT_SCRIPT = """
(function(){
  function qp(k){ try { return new URLSearchParams(location.search).get(k); } catch(e){ return null; } }
  function num(v){ var n=parseFloat(v); return isFinite(n)?n:null; }
  function enc(s){ return encodeURIComponent(s||''); }
  function stripNonCompliantAmapUi(){
    document.querySelectorAll('button, a').forEach(function(el){
      var t=(el.textContent||'').trim();
      if (/网页版/.test(t) || /^高德导航${'$'}/.test(t) || /在高德/.test(t) || /用高德地图查看/.test(t)) {
        el.remove();
      }
    });
    var bar=document.getElementById('lh-amap-bar');
    if (bar) bar.remove();
  }
  stripNonCompliantAmapUi();
  function buildAmapUrl(m){
    if (!m || typeof m !== 'object') return null;
    if (m.amapUrl && typeof m.amapUrl === 'string') return m.amapUrl;
    var action = (m.action || '').toLowerCase();
    if (!action && m.route) action = 'route';
    if (!action && m.lat != null && m.lng != null) action = 'view';
    if (action === 'route' && m.route) {
      var r = m.route;
      return 'amapuri://route/plan/?sid=&slat='+r.sLat+'&slon='+r.sLng+'&sname='+enc(r.sName||'起点')+'&did=&dlat='+r.dLat+'&dlon='+r.dLng+'&dname='+enc(r.dName||'终点')+'&dev=0&t='+(r.t||'0');
    }
    if (action === 'navi' && m.lat != null && m.lng != null) {
      return 'androidamap://navi?sourceApplication=littlehelper&poiname='+enc(m.name||'目的地')+'&lat='+m.lat+'&lon='+m.lng+'&dev=0';
    }
    if (m.lat != null && m.lng != null) {
      return 'androidamap://viewMap?sourceApplication=littlehelper&poiname='+enc(m.name||'目的地')+'&lat='+m.lat+'&lon='+m.lng+'&dev=0';
    }
    return null;
  }
  function resolveMapConfig(){
    var m = window.__LITTLEHELPER_MAP__;
    if (!m || typeof m !== 'object') m = {};
    if (!m.amapUrl) {
      if (m.lat == null) m.lat = num(qp('lh_lat')) || num(qp('lat'));
      if (m.lng == null) m.lng = num(qp('lh_lng')) || num(qp('lng')) || num(qp('lon'));
      if (!m.name) m.name = qp('lh_name') || qp('name') || '';
      if (!m.action) m.action = qp('lh_action') || qp('action') || '';
      if (!m.route) {
        var slat=num(qp('lh_slat')), slng=num(qp('lh_slng')), dlat=num(qp('lh_dlat')), dlng=num(qp('lh_dlng'));
        if (slat!=null && slng!=null && dlat!=null && dlng!=null) {
          m.route={sLat:slat,sLng:slng,sName:qp('lh_sname')||'起点',dLat:dlat,dLng:dlng,dName:qp('lh_dname')||'终点',t:qp('lh_t')||'0'};
        }
      }
    }
    var h1=document.querySelector('.header h1,h1');
    var title=h1?String(h1.textContent||'').replace(/[\uD83C-\uDBFF\uDC00-\uDFFF]+/g,'').trim():'';
    if (!m.route && /华亭/.test(title) && /天安门/.test(title)) {
      m.action='route';
      m.route={sLat:39.9785,sLng:116.3617,sName:'华亭嘉园',dLat:39.9087,dLng:116.3975,dName:'天安门',t:'0'};
    } else if (!m.lat && /华亭/.test(title) && !/天安门/.test(title)) {
      m.lat=39.9785; m.lng=116.3617; m.name=m.name||'华亭嘉园'; m.action=m.action||'view';
    }
    return m;
  }
  window.__LH_openAmap = function(){
    var url = buildAmapUrl(resolveMapConfig());
    if (url) location.href = url;
  };
  window.__LH_hasAmap = function(){
    return !!buildAmapUrl(resolveMapConfig());
  };
})();
""".trimIndent()
}
