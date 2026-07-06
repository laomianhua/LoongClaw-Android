#!/usr/bin/env python3
"""
文件管理器：列出 storage/ 中用户保存的文件（图片、PDF、Office等）
生成纵向列表 HTML，支持下载（画廊协议）+ 删除

用法: python scripts/file_manager.py
"""
import os, json, time

WORKSPACE = os.path.expanduser("~/.openclaw/workspace")
INDEX_FILE = os.path.join(WORKSPACE, "storage", "index.json")
CANVAS_DIR = os.path.join(
    os.environ.get("OPENCLAW_STATE_DIR", os.path.expanduser("~/.openclaw")),
    "canvas",
)
STORAGE_DIR = os.path.join(WORKSPACE, "storage")
UPLOAD_PORT = 18889

HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>文件管理</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,PingFang SC,Helvetica Neue,sans-serif;background:#0f1923;color:#e0e6ed;padding:16px;padding-bottom:calc(16px+env(safe-area-inset-bottom));min-height:100dvh;-webkit-touch-callout:none;-webkit-user-select:none;user-select:none}
h1{font-size:18px;color:#fff;margin-bottom:4px}
.sub{color:#7a8793;font-size:12px;margin-bottom:16px}
.section{font-size:14px;font-weight:600;color:#5a9aff;margin:16px 0 8px;padding:0 4px}
.file-list{display:flex;flex-direction:column;gap:2px}
.file-item{display:flex;align-items:center;padding:10px 8px;border-radius:8px;background:#1a2a3a;gap:12px;touch-action:manipulation;transition:background .15s}
.file-item:active,.file-item.pressing{background:#223344}
.file-icon{width:36px;height:36px;border-radius:6px;display:flex;align-items:center;justify-content:center;font-size:18px;flex-shrink:0}
.file-icon.img{background:#2a1a3a}
.file-icon.pdf{background:#3a1a1a}
.file-icon.doc{background:#1a2a4a}
.file-icon.other{background:#2a2a2a}
.file-info{flex:1;min-width:0}
.file-name{font-size:13px;font-weight:500;color:#fff;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.file-meta{font-size:11px;color:#5a6a7a;margin-top:2px}
.file-actions{display:flex;gap:6px;flex-shrink:0}
.file-actions button{width:32px;height:32px;border:none;border-radius:6px;font-size:14px;display:flex;align-items:center;justify-content:center;cursor:pointer;background:transparent;color:#7a8793;transition:all .15s}
.file-actions .btn-dl:active{background:#2a3a5a;color:#5a9aff}
.file-actions .btn-del:active{background:#3a2020;color:#f23645}
.toast{position:fixed;bottom:80px;left:50%;transform:translateX(-50%);background:#333;color:#fff;padding:8px 16px;border-radius:8px;font-size:13px;opacity:0;transition:opacity .3s;z-index:99;pointer-events:none}
.toast.show{opacity:1}
.modal-overlay{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.6);z-index:100;display:none;align-items:center;justify-content:center}
.modal-overlay.show{display:flex}
.modal-box{background:#1a2a3a;border-radius:12px;padding:20px;width:280px;text-align:center}
.modal-box p{font-size:14px;color:#e0e6ed;margin-bottom:16px;line-height:1.5}
.modal-btns{display:flex;gap:8px}
.modal-btns button{flex:1;padding:10px;border:none;border-radius:8px;font-size:13px;font-weight:500;cursor:pointer}
.btn-cancel{background:#2a3a4a;color:#7a8793}
.btn-confirm{background:#3a2020;color:#f23645}
.btn-cancel:active{background:#3a4a5a}
.btn-confirm:active{background:#5a3030}
@media (prefers-color-scheme:light){
body{background:#f5f7f9;color:#1a2a3a}
h1{color:#1a2a3a}
.file-item{background:#e8edf2}
.file-item:active,.file-item.pressing{background:#dce2ea}
.file-name{color:#1a2a3a}
.file-icon.img{background:#ead0f0}
.file-icon.pdf{background:#f0d0d0}
.file-icon.doc{background:#d0d8f0}
.file-icon.other{background:#e0e0e0}
.modal-box{background:#e8edf2}
.modal-box p{color:#1a2a3a}
.btn-cancel{background:#d0d8e0;color:#5a6a7a}
.btn-confirm{background:#f0d0d0;color:#c03020}
.toast{background:#666;color:#fff}
}
</style>
</head>
<body>
<h1>📁 我的文件</h1>
<div class="sub">%COUNT% 个文件 · 点 ⬇ 或长按行下载 · 点 🗑 删除</div>
<div class="file-list" id="fileList"></div>
<div class="toast" id="toast"></div>
<div class="modal-overlay" id="modalOverlay"><div class="modal-box"><p id="modalText"></p><div class="modal-btns"><button class="btn-cancel" id="modalCancel">取消</button><button class="btn-confirm" id="modalConfirm">删除</button></div></div></div>
<script>
(function(){
var host=location.hostname;
var port=%PORT%;
var items=%ITEMS%;

// 按画廊协议初始化 Gallery 数据（页面加载时设置）
var galleryItems=[];
var groupOrder=[];
var groups={};

function fmtSize(b){
if(b<1024)return b+'B';
if(b<1048576)return (b/1024).toFixed(1)+'KB';
return (b/1048576).toFixed(1)+'MB';
}
function fmtDate(t){return (t||'').substring(0,10)}
function iconCls(n,e){
var imgs=['jpg','jpeg','png','gif','webp','bmp'];
if(imgs.indexOf(e)>=0)return 'img';
if(e==='pdf')return 'pdf';
if(['doc','docx','xls','xlsx','ppt','pptx'].indexOf(e)>=0)return 'doc';
return 'other';
}
function iconChar(e){
var imgs=['jpg','jpeg','png','gif','webp','bmp'];
if(imgs.indexOf(e)>=0)return '🖼';
if(e==='pdf')return '📄';
if(['doc','docx'].indexOf(e)>=0)return '📝';
if(['xls','xlsx'].indexOf(e)>=0)return '📊';
if(['ppt','pptx'].indexOf(e)>=0)return '📽';
return '📁';
}
function extOf(n){var p=n.lastIndexOf('.');return p>0?n.substring(p+1).toLowerCase():'';}
function toast(m){
var t=document.getElementById('toast');t.textContent=m;t.classList.add('show');
setTimeout(function(){t.classList.remove('show')},2000);
}

var list=document.getElementById('fileList');
var catIco={'图片':'🖼','PDF':'📄','文档':'📝','其他':'📁'};

function rebuildGalleryItems(){
galleryItems=items.map(function(item){
var dl='http://'+host+':'+port+'/file/download/'+encodeURIComponent(item.fileName);
return {downloadUrl:dl,fileName:item.fileName,displayName:item.displayName};
});
window.__LITTLEHELPER_GALLERY__={title:'文件管理',items:galleryItems};
}

function buildGroups(){
groupOrder=[];groups={};
items.forEach(function(item,i){
var ext=extOf(item.fileName);
var dl='http://'+host+':'+port+'/file/download/'+encodeURIComponent(item.fileName);
var cat='其他';
if(['jpg','jpeg','png','gif','webp','bmp'].indexOf(ext)>=0)cat='图片';
else if(ext==='pdf')cat='PDF';
else if(['doc','docx','xls','xlsx','ppt','pptx'].indexOf(ext)>=0)cat='文档';
if(!groups[cat]){groups[cat]=[];groupOrder.push(cat)}
groups[cat].push({idx:i,item:item,ext:ext,dl:dl});
});
}

function renderList(){
rebuildGalleryItems();
buildGroups();
list.innerHTML='';
groupOrder.forEach(function(cat){
var sec=document.createElement('div');sec.className='section';
sec.textContent=(catIco[cat]||'📁')+' '+cat+'　'+groups[cat].length+'个';
list.appendChild(sec);
groups[cat].forEach(function(g){
var div=document.createElement('div');div.className='file-item';
div.innerHTML='<div class="file-icon '+iconCls(g.item.fileName,g.ext)+'">'+iconChar(g.ext)+'</div>'+
'<div class="file-info"><div class="file-name">'+g.item.displayName+'</div><div class="file-meta">'+fmtSize(g.item.size)+' · '+fmtDate(g.item.savedAt)+'</div></div>'+
'<div class="file-actions"><button class="btn-dl" data-idx="'+g.idx+'" data-name="'+g.item.displayName+'">⬇</button><button class="btn-del" data-idx="'+g.idx+'" data-name="'+g.item.displayName+'">🗑</button></div>';
list.appendChild(div);
});
});
document.querySelector('.sub').textContent=items.length+' 个文件 · 点 ⬇ 或长按行下载 · 点 🗑 删除';
if(items.length===0){
var empty=document.createElement('div');empty.className='sub';empty.style.textAlign='center';empty.style.marginTop='32px';empty.textContent='暂无文件';
list.appendChild(empty);
}
}

window.__LH_removeGalleryItem=function(idx){
if(idx<0||idx>=items.length)return false;
items.splice(idx,1);
renderList();
return true;
};

renderList();

// Delete modal
var modalOverlay=document.getElementById('modalOverlay');
var modalText=document.getElementById('modalText');
var modalCancel=document.getElementById('modalCancel');
var modalConfirm=document.getElementById('modalConfirm');
var pendingDel=null;
var pendingDelIdx=-1;

list.addEventListener('click',function(e){
// Download: 走画廊协议 deep link
var btn=e.target.closest('.btn-dl');
if(btn){
var idx=parseInt(btn.getAttribute('data-idx'));
location.href='littlehelper://gallery/download?index='+idx;
setTimeout(function(){toast('下载: '+btn.getAttribute('data-name'))},300);
return;
}
// Delete
var delBtn=e.target.closest('.btn-del');
if(delBtn){
var idx=parseInt(delBtn.getAttribute('data-idx'));
if(items[idx]){pendingDel=items[idx];pendingDelIdx=idx;modalText.textContent='确定要删除「'+pendingDel.displayName+'」吗？';modalOverlay.classList.add('show');}
}
});

modalCancel.addEventListener('click',function(){modalOverlay.classList.remove('show');pendingDel=null;pendingDelIdx=-1});
modalConfirm.addEventListener('click',function(){
if(!pendingDel||pendingDelIdx<0){modalOverlay.classList.remove('show');return}
location.href='littlehelper://gallery/delete?index='+pendingDelIdx;
modalOverlay.classList.remove('show');
pendingDel=null;
pendingDelIdx=-1;
});

// Long press download (skip action buttons)
var lt=null,sx,sy;
list.addEventListener('touchstart',function(e){
if(e.target.closest('.file-actions'))return;
var item=e.target.closest('.file-item');if(!item)return;
sx=e.touches[0].clientX;sy=e.touches[0].clientY;
item.classList.add('pressing');
lt=setTimeout(function(){lt=null;item.classList.remove('pressing');
var b=item.querySelector('.btn-dl');if(b)b.click();
},500);
},{passive:true});
document.addEventListener('touchmove',function(e){if(!lt||!e.touches)return;if(Math.abs(e.touches[0].clientX-sx)>12||Math.abs(e.touches[0].clientY-sy)>12){clearTimeout(lt);lt=null}},{passive:true});
document.addEventListener('touchend',function(){if(lt){clearTimeout(lt);lt=null}var els=list.querySelectorAll('.pressing');els.forEach(function(e){e.classList.remove('pressing')})},{passive:true});
})();
</script>
</body>
</html>
"""


def run():
    if not os.path.exists(INDEX_FILE):
        print(json.dumps({"error": "index.json not found"}, ensure_ascii=False))
        return

    with open(INDEX_FILE, "r", encoding="utf-8") as f:
        index = json.load(f)

    items = index.get("items", [])
    items = [i for i in items if "临时" not in i.get("tags", [])]

    for item in items:
        fn = item.get("fileName", "")
        fp = os.path.join(STORAGE_DIR, fn)
        try:
            item["size"] = os.path.getsize(fp)
        except:
            item["size"] = 0

    items.sort(key=lambda i: i.get("savedAt", ""), reverse=True)

    html = HTML_TEMPLATE
    html = html.replace("%COUNT%", str(len(items)))
    html = html.replace("%PORT%", str(UPLOAD_PORT))
    html = html.replace("%ITEMS%", json.dumps(items, ensure_ascii=False))

    os.makedirs(CANVAS_DIR, exist_ok=True)
    filepath = os.path.join(CANVAS_DIR, "file_manager.html")
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(html)

    print(json.dumps({"ok": True, "url": "/__openclaw__/canvas/file_manager.html", "count": len(items)}, ensure_ascii=False))


if __name__ == "__main__":
    run()
