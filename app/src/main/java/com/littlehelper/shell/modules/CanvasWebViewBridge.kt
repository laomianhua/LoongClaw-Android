package com.littlehelper.shell.modules

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/** 白板内嵌 Canvas WebView 与原生 UI（打开高德、保存原图/画廊）之间的薄桥接。 */
object CanvasWebViewBridge {

    private var webViewRef: WeakReference<WebView>? = null

    private val _amapAvailable = MutableStateFlow(false)
    val amapAvailable: StateFlow<Boolean> = _amapAvailable.asStateFlow()

    private val _storedImageAsset = MutableStateFlow<StoredImageAsset?>(null)
    val storedImageAsset: StateFlow<StoredImageAsset?> = _storedImageAsset.asStateFlow()

    private val _storedImageGallery = MutableStateFlow<StoredImageGallery?>(null)
    val storedImageGallery: StateFlow<StoredImageGallery?> = _storedImageGallery.asStateFlow()

    private val _galleryDownloadRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val galleryDownloadRequests: SharedFlow<Int> = _galleryDownloadRequests.asSharedFlow()

    fun attach(webView: WebView) {
        webViewRef = WeakReference(webView)
        _amapAvailable.value = false
        _storedImageAsset.value = null
        _storedImageGallery.value = null
    }

    fun detach(webView: WebView) {
        if (webViewRef?.get() === webView) {
            webViewRef = null
            _amapAvailable.value = false
            _storedImageAsset.value = null
            _storedImageGallery.value = null
        }
    }

    fun galleryItemAt(index: Int): StoredImageAsset? {
        val gallery = _storedImageGallery.value ?: return null
        return gallery.items.getOrNull(index)
    }

    fun requestGalleryDownload(index: Int): Boolean {
        if (galleryItemAt(index) == null) return false
        return _galleryDownloadRequests.tryEmit(index)
    }

    fun refreshAmapAvailability() {
        val webView = webViewRef?.get()
        if (webView == null) {
            _amapAvailable.value = false
            return
        }
        webView.evaluateJavascript(
            "(function(){try{return!!(window.__LH_hasAmap&&window.__LH_hasAmap())}catch(e){return false}})()"
        ) { result ->
            _amapAvailable.value = result == "true"
        }
    }

    /** 读取画廊 / 单图元数据；二者互斥，画廊优先。 */
    fun refreshMediaState() {
        val webView = webViewRef?.get()
        if (webView == null) {
            _storedImageGallery.value = null
            _storedImageAsset.value = null
            return
        }
        webView.evaluateJavascript(READ_GALLERY_SCRIPT) { galleryResult ->
            val gallery = WebViewJsResultParser.parseStoredImageGallery(galleryResult)
            _storedImageGallery.value = gallery
            if (gallery != null) {
                _storedImageAsset.value = null
                return@evaluateJavascript
            }
            webView.evaluateJavascript(READ_STORED_IMAGE_SCRIPT) { imageResult ->
                _storedImageAsset.value = WebViewJsResultParser.parseStoredImageAsset(imageResult)
            }
        }
    }

    fun openAmap() {
        webViewRef?.get()?.evaluateJavascript(
            "try{window.__LH_openAmap&&window.__LH_openAmap()}catch(e){}",
            null
        )
    }

    private const val READ_GALLERY_SCRIPT = """
(function(){
  try {
    var g = window.__LITTLEHELPER_GALLERY__;
    if (!g || typeof g !== 'object' || !g.items || !g.items.length) return null;
    var items = g.items.map(function(it){
      return {
        fileId: String((it && it.fileId) || ''),
        fileName: String((it && it.fileName) || ''),
        displayName: String((it && it.displayName) || ''),
        mimeType: String((it && it.mimeType) || 'image/jpeg'),
        downloadUrl: String((it && it.downloadUrl) || ''),
        thumbUrl: String((it && it.thumbUrl) || '')
      };
    }).filter(function(it){ return !!it.downloadUrl; });
    if (!items.length) return null;
    return JSON.stringify({ title: String(g.title || ''), items: items });
  } catch (e) {
    return null;
  }
})()
"""

    private const val READ_STORED_IMAGE_SCRIPT = """
(function(){
  function mimeFromName(name){
    var ext=(name||'').split('.').pop().toLowerCase();
    if(ext==='png') return 'image/png';
    if(ext==='webp') return 'image/webp';
    if(ext==='gif') return 'image/gif';
    return 'image/jpeg';
  }
  function pack(fileId,fileName,displayName,mimeType,downloadUrl){
    if(!downloadUrl) return null;
    return JSON.stringify({
      fileId:String(fileId||''),
      fileName:String(fileName||''),
      displayName:String(displayName||fileName||''),
      mimeType:String(mimeType||'image/jpeg'),
      downloadUrl:String(downloadUrl)
    });
  }
  try {
    var o=window.__LITTLEHELPER_IMAGE__;
    if(o&&typeof o==='object'&&o.downloadUrl){
      return pack(o.fileId,o.fileName,o.displayName||o.fileName,o.mimeType||'image/jpeg',o.downloadUrl);
    }
    var params=new URLSearchParams(location.search);
    var f=params.get('f');
    if(f){
      var name=params.get('name')||document.title||f;
      var fileId=f.split('_')[0]||'';
      return pack(fileId,f,name,mimeFromName(f),'/__openclaw__/file/download/'+encodeURIComponent(f));
    }
    var imgs=document.querySelectorAll('img');
    var best=null,bestArea=0;
    for(var i=0;i<imgs.length;i++){
      var img=imgs[i];
      var src=(img.currentSrc||img.src||'').trim();
      if(!src||src.indexOf('data:')===0) continue;
      var w=img.naturalWidth||img.width||0;
      var h=img.naturalHeight||img.height||0;
      var area=w*h;
      if(area>bestArea){bestArea=area;best=img;}
    }
    if(best){
      var src=(best.currentSrc||best.src||'').trim();
      var fileName=(best.alt||'image.jpg').trim()||'image.jpg';
      return pack('',fileName,fileName,mimeFromName(fileName),src);
    }
    return null;
  } catch(e) {
    return null;
  }
})()
"""
}
