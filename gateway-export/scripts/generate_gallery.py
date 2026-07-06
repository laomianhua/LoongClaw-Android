"""
生成画廊 HTML 文件 (v2 — 纵向单列 + 长按下载)
用法: python scripts/generate_gallery.py <keyword> [fileId1 fileId2 ...]
"""
import os, sys, json, time, shutil

WORKSPACE = os.path.expanduser("~/.openclaw/workspace")
INDEX_FILE = os.path.join(WORKSPACE, "storage", "index.json")
CANVAS_DIR = os.path.join(
    os.environ.get("OPENCLAW_STATE_DIR", os.path.expanduser("~/.openclaw")),
    "canvas",
)
UPLOAD_PORT = 18889
STORAGE_DIR = os.path.join(WORKSPACE, "storage")
MAX_ITEMS = 20

# HTML 头部
HTML_HEAD = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=3.0,user-scalable=no">
<title>图片列表</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#111;min-height:100dvh;padding:16px;padding-bottom:calc(16px+env(safe-area-inset-bottom));font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;-webkit-touch-callout:none;-webkit-user-select:none;user-select:none}
h1{color:#ddd;font-size:16px;font-weight:500;padding:0 0 16px}
.lh-gallery{display:flex;flex-direction:column;gap:24px;touch-action:pan-y}
.lh-item{border-radius:12px;overflow:hidden;background:#222;touch-action:manipulation;transition:transform .15s}
.lh-item img{max-width:100%;height:auto;display:block;pointer-events:none;margin:0 auto}
.lh-caption{color:#aaa;font-size:12px;padding:8px 10px}
.lh-item.lh-pressing{transform:scale(0.97);opacity:.8}
</style>
</head>
<body>
<h1>HEADER_HERE</h1>
<div class="lh-gallery" id="gallery"></div>
<script>
(function(){
 var host=location.hostname,port=PORT_HERE;
 var data=ITEMS_HERE;
 var items=data.items||[];
 items.forEach(function(item){
  if(!item.downloadUrl)item.downloadUrl='http://'+host+':'+port+'/file/download/'+encodeURIComponent(item.fileName);
  item.downloadUrl=item.downloadUrl.replace('__HOST__',host);
  if(!item.thumbUrl.startsWith('/')){item.thumbUrl=item.thumbUrl||item.downloadUrl;}
 });
 window.__LITTLEHELPER_GALLERY__={title:data.title||'',items:items};
 var g=document.getElementById('gallery');
 items.forEach(function(item,idx){
  var d=document.createElement('div');d.className='lh-item';d.setAttribute('data-index',idx);
  d.innerHTML='<img src="'+item.thumbUrl+'" alt="'+item.displayName+'" loading="lazy"/><div class="lh-caption">'+item.displayName+'</div>';
  g.appendChild(d);
  (function(i,el){
   var t=null,sx,sy;
   function c(){if(t){clearTimeout(t);t=null;el.classList.remove('lh-pressing')}}
   el.addEventListener('touchstart',function(e){
    if(!e.touches||!e.touches.length)return;
    sx=e.touches[0].clientX;sy=e.touches[0].clientY;c();el.classList.add('lh-pressing');
    t=setTimeout(function(){t=null;el.classList.remove('lh-pressing');location.href='littlehelper://gallery/download?index='+i;},500);
   },{passive:true});
   el.addEventListener('touchmove',function(e){
    if(!t||!e.touches||!e.touches.length)return;
    if(Math.abs(e.touches[0].clientX-sx)>12||Math.abs(e.touches[0].clientY-sy)>12)c();
   },{passive:true});
   el.addEventListener('touchend',c,{passive:true});
   el.addEventListener('touchcancel',c,{passive:true});
  })(idx,d);
 });
})();
</script>
</body>
</html>"""


def search_index(keyword, file_ids=None):
    if not os.path.exists(INDEX_FILE):
        return []
    with open(INDEX_FILE, "r", encoding="utf-8") as f:
        index = json.load(f)
    items = index.get("items", [])
    if file_ids:
        return [i for i in items if i.get("fileId") in file_ids]
    if keyword:
        kw = keyword.lower()
        results = []
        for i in items:
            if kw in i.get("displayName", "").lower():
                results.append(i)
            elif any(kw in t.lower() for t in i.get("tags", [])):
                results.append(i)
        return results
    return []


def make_gallery(items, title="图片"):
    items = items[:MAX_ITEMS]
    gallery_items = []
    for item in items:
        fn = item.get("fileName", "")
        display_name = item.get("displayName", "")
        # 复制图片到 canvas 目录
        src_path = os.path.join(STORAGE_DIR, fn)
        dst_path = os.path.join(CANVAS_DIR, fn)
        if os.path.exists(src_path) and not os.path.exists(dst_path):
            import shutil
            shutil.copy2(src_path, dst_path)
        canvas_url = "/__openclaw__/canvas/" + fn
        # 使用缩略图（400px）加速显示，不存在则用原图
        thumb_name = "thumb_" + fn
        thumb_canvas_path = os.path.join(CANVAS_DIR, thumb_name)
        if not os.path.exists(thumb_canvas_path) and os.path.exists(src_path):
            try:
                from PIL import Image
                img = Image.open(src_path)
                img.thumbnail((400,400), Image.LANCZOS)
                img.save(thumb_canvas_path, 'JPEG', quality=75)
            except:
                pass
        thumb_url = "/__openclaw__/canvas/" + thumb_name if os.path.exists(thumb_canvas_path) else canvas_url
        gallery_items.append({
            "fileId": item.get("fileId", ""),
            "fileName": fn,
            "displayName": display_name,
            "mimeType": item.get("mimeType", "image/jpeg"),
            "downloadUrl": "http://__HOST__:%d/file/download/%s" % (UPLOAD_PORT, fn),
            "thumbUrl": thumb_url
        })

    html = HTML_HEAD.replace("HEADER_HERE", "%s（共%d张）" % (title, len(items)))
    html = html.replace("PORT_HERE", str(UPLOAD_PORT))
    html = html.replace('ITEMS_HERE', json.dumps({"title": title, "items": gallery_items}, ensure_ascii=False))
    return html


def main():
    keyword = sys.argv[1] if len(sys.argv) > 1 else ""
    file_ids = sys.argv[2:] if len(sys.argv) > 2 else None

    items = search_index(keyword, file_ids)
    if not items:
        print(json.dumps({"error": "未找到匹配的记录", "keyword": keyword}, ensure_ascii=False))
        sys.exit(1)

    title = keyword + "相册" if keyword else "已保存图片"
    html = make_gallery(items, title)

    os.makedirs(CANVAS_DIR, exist_ok=True)
    ts = int(time.time() * 1000)
    filename = "gallery_cache_%d.html" % ts
    with open(os.path.join(CANVAS_DIR, filename), "w", encoding="utf-8") as f:
        f.write(html)

    result = {"ok": True, "file": filename, "url": "/__openclaw__/canvas/" + filename, "count": len(items), "title": title}
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
