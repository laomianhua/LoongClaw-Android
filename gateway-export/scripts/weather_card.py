#!/usr/bin/env python3
"""
天气查询卡片生成器
数据源：wttr.in（免费，无需 API Key）
用法:
  python weather_card.py                        # 默认北京
  python weather_card.py --city 上海             # 指定城市
  python weather_card.py --city 上海 --out mobile # 手机端紧凑版
"""
import urllib.request, json, sys, os
from datetime import datetime

sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

CANVAS_DIR = os.path.join(
    os.environ.get("OPENCLAW_STATE_DIR", os.path.expanduser("~/.openclaw")),
    "canvas",
)
DEFAULT_CITY = '北京'

# ── 天气图标映射 ────────────────────────────────────────
# wttr.in weatherCode → emoji
WEATHER_EMOJI = {
    '113': '☀️',  # Sunny
    '116': '⛅',  # Partly cloudy
    '119': '☁️',  # Cloudy
    '122': '☁️',  # Overcast
    '143': '🌫️',  # Mist
    '176': '🌦️',  # Patchy rain possible
    '179': '🌨️',  # Patchy snow possible
    '182': '🌧️',  # Patchy sleet possible
    '185': '🌧️',  # Patchy freezing drizzle
    '200': '⛈️',  # Thundery outbreaks
    '227': '🌨️',  # Blowing snow
    '230': '🌨️',  # Blizzard
    '248': '🌫️',  # Fog
    '260': '🌫️',  # Freezing fog
    '263': '🌧️',  # Patchy light drizzle
    '266': '🌧️',  # Light drizzle
    '281': '🌧️',  # Freezing drizzle
    '284': '🌧️',  # Heavy freezing drizzle
    '293': '🌧️',  # Patchy light rain
    '296': '🌧️',  # Light rain
    '299': '🌧️',  # Moderate rain
    '302': '🌧️',  # Moderate/heavy rain
    '305': '🌧️',  '308':'🌧️',
    '311': '🌧️', '314':'🌧️',
    '317': '🌧️', '320':'🌧️',
    '323': '🌨️', '326':'🌨️',
    '329': '🌨️', '332':'🌨️',
    '335': '🌨️', '338':'🌨️',
    '350': '🌨️', '353':'🌧️',
    '356': '🌧️', '359':'🌧️',
    '362': '🌧️', '365':'🌧️',
    '368': '🌨️', '371':'🌨️',
    '374': '🌨️', '377':'🌨️',
    '386': '⛈️', '389':'⛈️',
    '392': '⛈️', '395':'⛈️',
}

# 英文天气描述 → 中文
DESC_CN = {
    'Sunny': '晴', 'Clear': '晴',
    'Partly cloudy': '多云', 'Partly Cloudy ': '多云',
    'Cloudy': '多云', 'Overcast': '阴',
    'Smoky haze': '霾', 'Haze': '霾', 'Mist': '薄雾', 'Fog': '雾', 'Freezing fog': '冻雾',
    'Light drizzle': '小雨', 'Patchy light drizzle': '小阵雨',
    'Light rain': '小雨', 'Light rain shower': '阵雨', 'light rain shower': '阵雨',
    'Patchy light rain': '小阵雨', 'Patchy rain possible': '可能有雨',
    'Patchy rain nearby': '局部阵雨', 'Light rain shower': '阵雨',
    'Moderate rain': '中雨', 'Moderate rain at times': '间歇中雨',
    'Heavy rain': '大雨', 'Heavy rain at times': '间歇大雨',
    'Heavy Rain Shower': '大阵雨', 'Moderate or heavy rain shower': '中到大阵雨',
    'Torrential rain shower': '暴雨',
    'Light sleet': '小冻雨', 'Moderate or heavy sleet': '冻雨',
    'Light snow': '小雪', 'Patchy light snow': '小阵雪',
    'Moderate snow': '中雪', 'Heavy snow': '大雪',
    'Blowing snow': '吹雪', 'Blizzard': '暴风雪',
    'Thundery outbreaks possible': '可能有雷暴',
    'Patchy light rain with thunder': '小雷阵雨',
    'Moderate or heavy rain with thunder': '雷阵雨',
}

def translate_desc(desc: str) -> str:
    parts = desc.split(',')
    translated = []
    for p in parts:
        p = p.strip()
        # 大小写不敏感匹配
        found = DESC_CN.get(p, None)
        if found is None:
            found = DESC_CN.get(p.lower(), p)
        translated.append(found)
    seen = set()
    result = []
    for t in translated:
        if t not in seen:
            seen.add(t)
            result.append(t)
    return '、'.join(result[:3])

# ── 数据拉取 ────────────────────────────────────────────
def fetch_weather(city: str) -> dict:
    """从 wttr.in 获取天气"""
    try:
        url = f'https://wttr.in/{urllib.request.quote(city)}?format=j1'
        req = urllib.request.Request(url, headers={'User-Agent': 'curl/7.0'})
        r = urllib.request.urlopen(req, timeout=10)
        data = json.loads(r.read().decode('utf-8'))
        return data
    except Exception as e:
        return {'error': str(e)}


def emoji_for(code: str) -> str:
    return WEATHER_EMOJI.get(str(code), '🌤️')


def parse_weather(data: dict, input_city: str = '') -> dict:
    """解析 wttr.in JSON 为卡片数据"""
    if 'error' in data:
        return data

    try:
        cc = data['current_condition'][0]
        today = data['weather'][0]
    except (KeyError, IndexError):
        return {'error': '数据格式异常'}

    # 3日预报
    forecast = []
    for w in data['weather'][:3]:
        date_str = w['date']
        try:
            dt = datetime.strptime(date_str, '%Y-%m-%d')
            month_day = f'{dt.month}/{dt.day}'
            dow_cn = ['周一','周二','周三','周四','周五','周六','周日'][dt.weekday()]
        except:
            month_day = date_str[5:] if len(date_str) >= 10 else date_str
            dow_cn = ''
        forecast.append({
            'date': w['date'],
            'label': dow_cn,
            'month_day': month_day,
            'max': w['maxtempC'],
            'min': w['mintempC'],
            'code': w['hourly'][0]['weatherCode'] if w.get('hourly') else '116',
        })

    return {
        'city': input_city,  # 用用户输入的城市名，不用 API 返回的区名
        'country': data.get('nearest_area', [{}])[0].get('country', [{}])[0].get('value', ''),
        'temp': cc.get('temp_C', '--'),
        'feels': cc.get('FeelsLikeC', '--'),
        'desc': translate_desc(cc.get('weatherDesc', [{}])[0].get('value', '')),
        'code': cc.get('weatherCode', '116'),
        'humidity': cc.get('humidity', '--'),
        'wind': cc.get('windspeedKmph', '--'),
        'uv': cc.get('uvIndex', '--'),
        'max': today['maxtempC'],
        'min': today['mintempC'],
        'forecast': forecast,
        'updated': datetime.now().strftime('%Y-%m-%d %H:%M'),
    }


# ── HTML 渲染 ────────────────────────────────────────────
def render_html(w: dict, mode: str) -> str:
    if 'error' in w:
        return f'<html><body style="background:#1a1a2e;color:#f85149;padding:24px;font-family:sans-serif"><h2>❌ 查询失败</h2><p>{w["error"]}</p></body></html>'

    is_mobile = (mode == 'mobile')
    emoji = emoji_for(w['code'])

    # 预报行
    fc_html = ''
    for f in w['forecast']:
        fc_html += f'''
        <div class="fc-day" data-date="{f['date']}">
            <div class="dow">{f['label']} {f['month_day']}</div>
            <div class="hi">{f['max']}°</div>
            <div class="lo">{f['min']}°</div>
            <div class="cond">{emoji_for(f['code'])}</div>
        </div>'''

    css = f'''*{{margin:0;padding:0;box-sizing:border-box}}
body{{background:#0d1117;color:#e6edf3;min-height:100dvh;padding:{'16px' if is_mobile else '24px'};font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif}}
.city{{font-size:13px;color:#8b949e;margin-bottom:2px}}
.date{{font-size:12px;color:#6e7681;margin-bottom:16px}}
.main{{background:linear-gradient(135deg,#16213e 0%,#1a1a4e 100%);border-radius:16px;padding:{'20px' if is_mobile else '28px'};text-align:center;margin-bottom:16px}}
.main .temp{{font-size:{'56px' if is_mobile else '64px'};font-weight:700;color:#fff;line-height:1}}
.main .desc{{font-size:16px;color:#e6edf3;margin-top:6px}}
.main .feels{{font-size:13px;color:#8b949e;margin-top:4px}}
.main .range{{font-size:14px;color:#b0b8c4;margin-top:8px}}
.details{{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin-bottom:16px}}
.det{{background:#161b22;border:1px solid #21262d;border-radius:12px;padding:12px 8px;text-align:center}}
.det .num{{font-size:20px;font-weight:600;color:#e6edf3;display:block}}
.det .lbl{{font-size:11px;color:#6e7681;margin-top:4px;display:block}}
.ftitle{{font-size:14px;color:#8b949e;margin-bottom:10px;margin-top:4px}}
.forecast{{display:flex;gap:8px}}
.fc-day{{flex:1;background:#161b22;border:1px solid #21262d;border-radius:12px;padding:14px 8px;text-align:center}}
.fc-day .dow{{font-size:12px;color:#8b949e;margin-bottom:4px}}
.fc-day .hi{{font-size:16px;font-weight:600;color:#f85149}}
.fc-day .lo{{font-size:13px;color:#3fb950;margin-top:2px}}
.fc-day .cond{{font-size:14px;margin-top:4px}}
.footer{{text-align:center;color:#484f58;font-size:11px;margin-top:14px}}'''

    date_js = '''(function(){var d=new URLSearchParams(location.search).get('date');if(!d)return;var el=document.querySelector('.fc-day[data-date="'+d+'"]');if(!el)return;el.scrollIntoView({behavior:'smooth',block:'center',inline:'center'});el.style.border='2px solid #58a6ff';el.style.background='#1c2333'})();'''
    return f'''<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>{w['city']}天气</title><style>{css}</style></head><body>
<div class="city">🌍 {w['city']}{', '+w['country'] if w['country'] and w['country'] != 'China' else ''}</div>
<div class="date">{w['updated']} 更新 · 体感{w['feels']}°C</div>
<div class="main"><div class="temp">{w['temp']}°</div>
<div class="desc">{emoji} {w['desc']}</div>
<div class="feels">体感 {w['feels']}°C</div>
<div class="range">↗ {w['max']}° / ↘ {w['min']}°</div></div>
<div class="details">
<div class="det"><span class="num">{w['humidity']}%</span><span class="lbl">湿度</span></div>
<div class="det"><span class="num">{w['wind']}</span><span class="lbl">风速 km/h</span></div>
<div class="det"><span class="num">{w['uv']}</span><span class="lbl">紫外线</span></div></div>
<div class="ftitle">📅 未来预报</div>
<div class="forecast">{fc_html}</div>
<div class="footer">数据来源: wttr.in · 仅供参考</div>
<script>
{date_js}
</script>
</body></html>'''


# ── 主入口 ────────────────────────────────────────────────
if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser()
    p.add_argument('--city', default=DEFAULT_CITY)
    p.add_argument('--out', choices=['mobile', 'desktop'], default='mobile')
    p.add_argument('--date', default='', help='锚定日期 YYYY-MM-DD')
    args = p.parse_args()

    raw = fetch_weather(args.city)
    w = parse_weather(raw, args.city)

    html = render_html(w, args.out)

    # 城市 key 用于文件名（ASCII 安全）
    city_map = {'北京':'beijing','上海':'shanghai','广州':'guangzhou','深圳':'shenzhen',
                '杭州':'hangzhou','成都':'chengdu','南京':'nanjing','武汉':'wuhan'}
    city_key = city_map.get(args.city, args.city.lower().replace(' ', '_'))
    filename = f'weather_{city_key}.html'
    filepath = os.path.join(CANVAS_DIR, filename)
    os.makedirs(CANVAS_DIR, exist_ok=True)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(html)

    print(json.dumps({
        'ok': 'error' not in w,
        'city': w.get('city', args.city),
        'temp': w.get('temp', '--'),
        'desc': w.get('desc', ''),
        'url': f'/__openclaw__/canvas/{filename}{'?date='+args.date if args.date else ''}',
    }, ensure_ascii=False))
