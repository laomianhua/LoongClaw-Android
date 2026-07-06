#!/usr/bin/env python3
"""
智能记事本：月历 + 日详情 + 月份切换 + 停车
用法: python scripts/notepad.py --show --out mobile
"""
import os, json, calendar
from datetime import date

WORKSPACE = os.path.expanduser("~/.openclaw/workspace")
DATA_FILE = os.path.join(WORKSPACE, "beancount/data/notepad.json")
CANVAS_DIR = os.path.join(
    os.environ.get("OPENCLAW_STATE_DIR", os.path.expanduser("~/.openclaw")),
    "canvas",
)

HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>记事本</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,PingFang SC,Helvetica Neue,sans-serif;background:#0f1923;color:#e0e6ed;padding:16px;padding-bottom:calc(16px+env(safe-area-inset-bottom));min-height:100dvh}
h1{font-size:20px;color:#fff;margin-bottom:2px}
.sub{color:#7a8793;font-size:12px;margin-bottom:16px}
.parking-card{background:linear-gradient(135deg,#1a2a3a,#15202e);border-radius:10px;padding:14px 16px;margin-bottom:16px;display:flex;align-items:center;gap:10px}
.parking-icon{font-size:24px;flex-shrink:0}
.parking-info{flex:1}
.parking-label{font-size:11px;color:#7a8793}
.parking-loc{font-size:18px;font-weight:700;color:#5a9aff}
.parking-time{font-size:10px;color:#5a6a7a;margin-top:2px}
.calendar{background:#1a2a3a;border-radius:12px;padding:14px;margin-bottom:12px}
.month-nav{display:flex;align-items:center;justify-content:space-between;padding-bottom:10px;border-bottom:1px solid #2a3a4a;margin-bottom:8px}
.month-nav .nav-btn{width:36px;height:36px;border-radius:50%;border:none;background:#2a3a4a;color:#e0e6ed;font-size:18px;cursor:pointer;display:flex;align-items:center;justify-content:center;-webkit-tap-highlight-color:transparent;touch-action:manipulation}
.month-nav .nav-btn:active{background:#3a4a5a}
.month-nav .month-title{font-size:16px;font-weight:600;color:#fff}
.weekdays{display:grid;grid-template-columns:repeat(7,1fr);text-align:center;font-size:11px;color:#5a6a7a;padding:4px 0;gap:2px}
.weekday.sun{color:#f23645}
.weekday.sat{color:#5a9aff}
.days-grid{display:grid;grid-template-columns:repeat(7,1fr);gap:2px;margin-top:2px}
.day-cell{aspect-ratio:1;border-radius:8px;display:flex;flex-direction:column;align-items:center;justify-content:center;font-size:13px;position:relative;min-height:44px;padding:2px;cursor:pointer;-webkit-tap-highlight-color:transparent}
.day-cell:active{background:#2a3a4a}
.day-cell.other-month{visibility:hidden;pointer-events:none}
.day-cell.weekend{color:#5a6a7a}
.day-cell.today{background:#5a9aff;color:#0f1923;font-weight:700}
.day-cell.selected{box-shadow:0 0 0 2px #5a9aff;background:#2a3a4a}
.day-cell.today.selected{background:#5a9aff;box-shadow:0 0 0 3px #7abaff}
.day-cell .day-num{font-size:13px;font-weight:500}
.day-cell.today .day-num{font-size:15px;font-weight:700}
.day-cell .dot-wrap{display:flex;gap:2px;margin-top:1px;min-height:5px}
.day-cell .dot{width:4px;height:4px;border-radius:50%}
.day-cell .dot.normal{background:#ffb347}
.day-cell .dot.recur{background:#5a9aff}
.day-cell.today .dot.normal{background:#0f1923}
.day-cell.today .dot.recur{background:#0f1923}
.detail-card{background:#1a2a3a;border-radius:12px;padding:14px;margin-top:4px}
.detail-header{font-size:15px;font-weight:600;color:#fff;margin-bottom:10px;display:flex;align-items:center;gap:8px}
.detail-header .ddow{font-size:12px;color:#7a8793;font-weight:400}
.detail-header .dtoday{font-size:10px;background:#5a9aff;color:#0f1923;padding:1px 6px;border-radius:3px;font-weight:700}
.detail-empty{color:#5a6a7a;font-size:13px;text-align:center;padding:16px 0}
.detail-row{display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid #1e2e3e}
.detail-row:last-child{border-bottom:none}
.detail-row .dicon{font-size:14px;width:20px;text-align:center;flex-shrink:0}
.detail-row .dtext{font-size:13px;color:#e0e6ed;flex:1}
.detail-row .dbadge{font-size:10px;color:#5a9aff;background:#1a2a4a;padding:2px 6px;border-radius:4px;flex-shrink:0}

/* 年月选择器 */
.picker-overlay{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.6);z-index:100;display:none;align-items:center;justify-content:center}
.picker-overlay.show{display:flex}
.picker-box{background:#1a2a3a;border-radius:14px;padding:20px;width:280px}
.picker-box .p-title{text-align:center;font-size:15px;font-weight:600;color:#fff;margin-bottom:12px}
.picker-year{display:flex;justify-content:center;align-items:center;gap:12px;margin-bottom:12px}
.picker-year button{width:32px;height:32px;border:none;border-radius:50%;background:#2a3a4a;color:#e0e6ed;font-size:16px;cursor:pointer}
.picker-year button:active{background:#3a4a5a}
.picker-year .py-text{font-size:16px;color:#5a9aff;font-weight:600;min-width:60px;text-align:center}
.picker-months{display:grid;grid-template-columns:repeat(4,1fr);gap:6px}
.picker-months button{padding:10px;border:none;border-radius:8px;background:#2a3a4a;color:#e0e6ed;font-size:13px;cursor:pointer}
.picker-months button:active{background:#3a4a5a}
.picker-months button.p-cur{background:#5a9aff;color:#0f1923;font-weight:700}
.picker-months button.p-today{box-shadow:0 0 0 1px #5a9aff}
.picker-close{display:block;width:100%;margin-top:12px;padding:8px;border:none;border-radius:8px;background:#2a3a4a;color:#7a8793;font-size:12px;cursor:pointer}
.footer{color:#5a6a7a;font-size:11px;text-align:center;padding:16px 0 24px}
@media (prefers-color-scheme:light){
body{background:#f5f7f9;color:#1a2a3a}
h1{color:#1a2a3a}
.parking-card{background:#e0e8f0}
.parking-loc{color:#2a6aff}
.calendar{background:#e8edf2}
.month-nav .nav-btn{background:#d0d8e0;color:#1a2a3a}
.month-nav .nav-btn:active{background:#c0c8d0}
.month-nav .month-title{color:#1a2a3a}
.day-cell:active{background:#d0d8e0}
.day-cell.today{background:#2a6aff;color:#fff}
.day-cell.selected{background:#d0d8e0;box-shadow:0 0 0 2px #2a6aff}
.day-cell.today.selected{background:#2a6aff}
.day-cell.today .dot.normal{background:#fff}
.day-cell.today .dot.recur{background:#fff}
.detail-card{background:#e8edf2}
.detail-header{color:#1a2a3a}
.detail-row{border-bottom:1px solid #d0d8e0}
.detail-row .dtext{color:#1a2a3a}
.detail-row .dbadge{background:#d0d8f0;color:#2a6aff}
}
</style>
</head>
<body>

<h1>📋 记事本</h1>
<div class="sub">%TODAY_LABEL%</div>

<div class="parking-card">
<div class="parking-icon">🅿️</div>
<div class="parking-info">
<div class="parking-label">停车位置</div>
<div class="parking-loc">%PARKING_LOC%</div>
<div class="parking-time">最后更新 %PARKING_TIME%</div>
</div>
</div>

<div class="calendar">
<div class="month-nav">
<button class="nav-btn" id="prevMonth">‹</button>
<div class="month-title" id="monthTitle">%MONTH_LABEL%</div>
<button class="nav-btn" id="nextMonth">›</button>
</div>

<div class="picker-overlay" id="pickerOverlay">
<div class="picker-box">
<div class="p-title">选择月份</div>
<div class="picker-year">
<button id="pyPrev">‹</button>
<span class="py-text" id="pyText">%YEAR%</span>
<button id="pyNext">›</button>
</div>
<div class="picker-months" id="pickerMonths"></div>
<button class="picker-close" id="pickerClose">关闭</button>
</div>
</div>
<div class="weekdays">
<div>一</div><div>二</div><div>三</div><div>四</div><div>五</div><div class="sat">六</div><div class="sun">日</div>
</div>
<div class="days-grid" id="daysGrid">
%CALENDAR_CELLS%
</div>
</div>

<div class="detail-card" id="detailCard">
<div class="detail-header" id="detailHeader">选择日期查看</div>
<div id="detailContent"><div class="detail-empty">👆 点击日历中的某天查看详情</div></div>
</div>

<div class="footer" id="footer">数据来源：记事本</div>

<script>
var ALL_EVENTS = %ALL_EVENTS_JSON%;
var DAILY_REC = %DAILY_REC_JSON%;
var DOW_CN = ["周日","周一","周二","周三","周四","周五","周六"];
var TODAY_STR = "%TODAY_ISO%";
var curYear = %YEAR%, curMonth = %MONTH%;
var selDay = %TODAY_DAY%;

function daysInMonth(y,m){return new Date(y,m,0).getDate()}
function firstWeekday(y,m){var d=new Date(y,m-1,1);return(d.getDay()+6)%7} // 0=Mon

function renderCalendar(){
var grid=document.getElementById('daysGrid');
var dim=daysInMonth(curYear,curMonth);
var fwd=firstWeekday(curYear,curMonth);

var cells=[];
for(var i=0;i<fwd;i++)cells.push('<div class="day-cell other-month"></div>');

for(var d=1;d<=dim;d++){
var dt=curYear+'-'+(curMonth<10?'0':'')+curMonth+'-'+(d<10?'0':'')+d;
var isToday=(dt===TODAY_STR);
var isWeekend=[5,6].indexOf((fwd+d-1)%7)>=0;

var hasNormal=false,hasRec=false;
if(ALL_EVENTS[dt])hasNormal=true;
if(DAILY_REC.length>0)hasRec=true;

var dots='';
if(hasNormal&&hasRec)dots='<div class="dot-wrap"><div class="dot normal"></div><div class="dot recur"></div></div>';
else if(hasNormal)dots='<div class="dot-wrap"><div class="dot normal"></div></div>';
else if(hasRec)dots='<div class="dot-wrap"><div class="dot recur"></div></div>';

var cls='day-cell'+(isToday?' today':'')+(isWeekend?' weekend':'');
var sel=(d===selDay)?' selected':'';
cells.push('<div class="'+cls+sel+'" data-day="'+d+'"><div class="day-num">'+d+'</div>'+dots+'</div>');
}

var total=fwd+dim;
var rem=7-(total%7);
if(rem<7)for(var i=0;i<rem;i++)cells.push('<div class="day-cell other-month"></div>');
grid.innerHTML=cells.join('\n');

document.getElementById('monthTitle').textContent=curYear+'年'+curMonth+'月';

// 高亮选中
if(selDay&&selDay<=dim){
var sc=grid.querySelector('.day-cell[data-day="'+selDay+'"]');
if(sc)sc.classList.add('selected');
}
}

function showDay(day){
var dt=curYear+'-'+(curMonth<10?'0':'')+curMonth+'-'+(day<10?'0':'')+day;
var dim=daysInMonth(curYear,curMonth);
var dowIdx=(firstWeekday(curYear,curMonth)+day-1)%7;
var isToday=(dt===TODAY_STR);

// 更新选中高亮
selDay=day;
document.querySelectorAll('.day-cell.selected').forEach(function(c){c.classList.remove('selected')});
var sc=document.getElementById('daysGrid').querySelector('.day-cell[data-day="'+day+'"]');
if(sc)sc.classList.add('selected');

// 详情
document.getElementById('detailHeader').innerHTML=curMonth+'月'+day+'日 <span class="ddow">'+DOW_CN[dowIdx]+'</span>'+(isToday?' <span class="dtoday">今天</span>':'');

var evs=ALL_EVENTS[dt]||[];
var html='';
if(evs.length===0&&DAILY_REC.length===0){
html='<div class="detail-empty">✅ 无事</div>';
}else{
evs.forEach(function(t){html+='<div class="detail-row"><span class="dicon">•</span><span class="dtext">'+t+'</span></div>';});
DAILY_REC.forEach(function(e){html+='<div class="detail-row"><span class="dicon">🔄</span><span class="dtext">'+e.text+'</span><span class="dbadge">每日</span></div>';});
}
document.getElementById('detailContent').innerHTML=html;
}

// 月份导航
function goMonth(y,m){curYear=y;curMonth=m;selDay=Math.min(selDay,daysInMonth(curYear,curMonth));renderCalendar();showDay(selDay)}
document.getElementById('prevMonth').addEventListener('click',function(){var m=curMonth-1,y=curYear;if(m<1){m=12;y--}goMonth(y,m)});
document.getElementById('nextMonth').addEventListener('click',function(){var m=curMonth+1,y=curYear;if(m>12){m=1;y++}goMonth(y,m)});

// 年月选择器（点击标题弹出）
var pickerYear=%YEAR%;
document.getElementById('monthTitle').addEventListener('click',function(){pickerYear=curYear;renderPicker();document.getElementById('pickerOverlay').classList.add('show')});
document.getElementById('pyPrev').addEventListener('click',function(){pickerYear--;renderPicker()});
document.getElementById('pyNext').addEventListener('click',function(){pickerYear++;renderPicker()});
document.getElementById('pickerClose').addEventListener('click',function(){document.getElementById('pickerOverlay').classList.remove('show')});
function renderPicker(){
document.getElementById('pyText').textContent=pickerYear;
var html='';
for(var i=1;i<=12;i++){
var cls='';
if(pickerYear===curYear&&i===curMonth)cls=' p-cur';
if(pickerYear===%YEAR%&&i===%MONTH%)cls=cls?cls+' p-today':'p-today';
html+='<button class="'+cls+'" data-m="'+i+'">'+i+'月</button>';
}
document.getElementById('pickerMonths').innerHTML=html;
}
document.getElementById('pickerMonths').addEventListener('click',function(e){
var btn=e.target.closest('button');
if(!btn||!btn.getAttribute('data-m'))return;
document.getElementById('pickerOverlay').classList.remove('show');
goMonth(pickerYear,parseInt(btn.getAttribute('data-m')));
});

// 点击日历格子
document.getElementById('daysGrid').addEventListener('click',function(e){
var cell=e.target.closest('.day-cell');
if(!cell||cell.classList.contains('other-month'))return;
var d=parseInt(cell.getAttribute('data-day'));
showDay(d);
});

// 默认显示今天
renderCalendar();
showDay(%TODAY_DAY%);
</script>
</body>
</html>
"""


def load_data():
    if not os.path.exists(DATA_FILE):
        return {"parking": {}, "events": []}
    with open(DATA_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def is_recurring_daily(e):
    return e.get("type") == "recurring" and e.get("recurrence") == "daily"


def build_html(data):
    parking = data.get("parking", {})
    events = data.get("events", [])

    parking_loc = parking.get("location", "未记录")
    parking_time = (parking.get("updated", "")[:10]) if parking.get("updated") else ""

    today = date.today()
    this_year, this_month = today.year, today.month
    _, days_in_month = calendar.monthrange(this_year, this_month)
    first_weekday = date(this_year, this_month, 1).weekday()

    daily_rec = [e for e in events if is_recurring_daily(e)]

    # 所有事件按日期索引（不限月份），前端 JS 渲染
    all_events = {}
    for e in events:
        if is_recurring_daily(e):
            continue
        ed = e.get("date", "")
        if ed:
            all_events.setdefault(ed, []).append(e.get("text", ""))

    # 当前月日历格子生成
    cells = []
    for _ in range(first_weekday):
        cells.append('<div class="day-cell other-month"></div>')

    for d in range(1, days_in_month + 1):
        dt = date(this_year, this_month, d)
        is_today = (dt == today)
        is_weekend = dt.weekday() >= 5
        dt_str = dt.isoformat()
        has_normal = dt_str in all_events
        has_rec = len(daily_rec) > 0

        dots = ""
        if has_normal and has_rec:
            dots = '<div class="dot-wrap"><div class="dot normal"></div><div class="dot recur"></div></div>'
        elif has_normal:
            dots = '<div class="dot-wrap"><div class="dot normal"></div></div>'
        elif has_rec:
            dots = '<div class="dot-wrap"><div class="dot recur"></div></div>'

        cls = "day-cell"
        if is_today: cls += " today"
        if is_weekend: cls += " weekend"

        cells.append(f'<div class="{cls}" data-day="{d}"><div class="day-num">{d}</div>{dots}</div>')

    total_cells = first_weekday + days_in_month
    remainder = 7 - (total_cells % 7)
    if remainder < 7:
        for _ in range(remainder):
            cells.append('<div class="day-cell other-month"></div>')

    all_events_json = json.dumps(all_events, ensure_ascii=False)
    daily_rec_json_str = json.dumps([{"text": e.get("text", "")} for e in daily_rec], ensure_ascii=False)

    today_label = today.strftime("%Y年%m月%d日") + " " + ['周一','周二','周三','周四','周五','周六','周日'][today.weekday()]
    month_label = f"{this_year}年{this_month}月"

    html = HTML_TEMPLATE
    html = html.replace("%TODAY_LABEL%", today_label)
    html = html.replace("%PARKING_LOC%", parking_loc)
    html = html.replace("%PARKING_TIME%", parking_time)
    html = html.replace("%MONTH_LABEL%", month_label)
    html = html.replace("%CALENDAR_CELLS%", "\n".join(cells))
    html = html.replace("%ALL_EVENTS_JSON%", all_events_json)
    html = html.replace("%DAILY_REC_JSON%", daily_rec_json_str)
    html = html.replace("%YEAR%", str(this_year))
    html = html.replace("%MONTH%", str(this_month))
    html = html.replace("%TODAY_ISO%", today.isoformat())
    html = html.replace("%TODAY_DAY%", str(today.day))

    return html


def main():
    data = load_data()
    html = build_html(data)
    os.makedirs(CANVAS_DIR, exist_ok=True)
    filepath = os.path.join(CANVAS_DIR, "notepad_view.html")
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(html)
    print(json.dumps({"ok": True, "url": "/__openclaw__/canvas/notepad_view.html"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
