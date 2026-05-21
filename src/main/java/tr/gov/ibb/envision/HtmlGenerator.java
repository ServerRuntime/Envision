package tr.gov.ibb.envision;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Env variable listesini alıp kendi kendine yeten tek bir HTML dosyası üretir.
 * Harici bağımlılık yok — CSS ve JS tamamen inline.
 */
public class HtmlGenerator {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    /** PIN koruması olmadan üretir. */
    public String generate(List<EnvVariable> vars, boolean maskSensitive) {
        return generate(vars, maskSensitive, "");
    }

    /**
     * @param pin  Boş string → PIN koruması yok. Dolu → hassas kopyalama PIN gerektirir.
     */
    public String generate(List<EnvVariable> vars, boolean maskSensitive, String pin) {
        Map<String, List<EnvVariable>> grouped = vars.stream()
                .collect(Collectors.groupingBy(EnvVariable::category, TreeMap::new, Collectors.toList()));

        grouped.values().forEach(list ->
                list.sort(Comparator.comparing(EnvVariable::key, String.CASE_INSENSITIVE_ORDER)));

        String generatedAt = LocalDateTime.now().format(FMT);
        int sensitiveCount = (int) vars.stream().filter(EnvVariable::sensitive).count();

        // Gerçek değerler JSON'a gömülür — maskeleme ve PIN kontrolü JS tarafında yapılır.
        String jsonData = buildJsonData(grouped);
        String pinHash  = hashPin(pin);

        return buildHtml(generatedAt, vars.size(), sensitiveCount, grouped.size(), jsonData, pinHash);
    }

    // ── JSON builder — gerçek değerleri gömer, maskeleme JS'e bırakılır ──────
    private String buildJsonData(Map<String, List<EnvVariable>> grouped) {
        StringBuilder sb = new StringBuilder("[");
        boolean firstGroup = true;
        for (Map.Entry<String, List<EnvVariable>> entry : grouped.entrySet()) {
            if (!firstGroup) sb.append(",");
            firstGroup = false;
            sb.append("{\"category\":\"").append(escJson(entry.getKey())).append("\",\"items\":[");
            boolean firstItem = true;
            for (EnvVariable v : entry.getValue()) {
                if (!firstItem) sb.append(",");
                firstItem = false;
                sb.append("{")
                  .append("\"key\":\"").append(escJson(v.key())).append("\",")
                  .append("\"value\":\"").append(escJson(v.value())).append("\",")
                  .append("\"sensitive\":").append(v.sensitive())
                  .append("}");
            }
            sb.append("]}");
        }
        sb.append("]");
        return sb.toString();
    }

    // ── PIN → SHA-256 hex hash ─────────────────────────────────────────────
    private static String hashPin(String pin) {
        if (pin == null || pin.isBlank()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 kullanılamadı", e);
        }
    }

    private static String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ── HTML ───────────────────────────────────────────────────────────────
    private String buildHtml(String generatedAt, int total, int sensitive,
                             int categories, String jsonData, String pinHash) {
        // Placeholder'lar ile template oluştur, sonra güvenli replace yap.
        // String.format/formatted KULLANILMIYOR — env değerleri % içerebilir.
        return TEMPLATE
            .replace("{{TOTAL}}",      String.valueOf(total))
            .replace("{{SENSITIVE}}",  String.valueOf(sensitive))
            .replace("{{CATEGORIES}}", String.valueOf(categories))
            .replace("{{GENERATED}}",  generatedAt)
            .replace("{{JSON_DATA}}",  jsonData)
            .replace("{{PIN_HASH}}",   pinHash);
    }

    private static final String TEMPLATE = """
<!DOCTYPE html>
<html lang="tr" data-theme="dark">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>Envision</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
<style>
:root {
  --bg:#07090f; --surface:rgba(255,255,255,.04); --solid:#0e1117;
  --surface2:rgba(255,255,255,.07); --border:rgba(255,255,255,.08);
  --border2:rgba(255,255,255,.14); --text:#f0f4ff; --t2:#8b95a9; --t3:#505a6e;
  --accent:#6366f1; --accent2:#818cf8; --glow:rgba(99,102,241,.25);
  --green:#34d399; --gdim:rgba(52,211,153,.12);
  --yellow:#fbbf24; --ydim:rgba(251,191,36,.12);
  --red:#f87171;   --rdim:rgba(248,113,113,.12);
  --blue:#60a5fa;  --bdim:rgba(96,165,250,.12);
  --r:12px; --rs:8px;
}
[data-theme="light"] {
  --bg:#f1f3f9; --surface:rgba(255,255,255,.95); --solid:#fff;
  --surface2:rgba(0,0,0,.04); --border:rgba(0,0,0,.08); --border2:rgba(0,0,0,.16);
  --text:#0f172a; --t2:#374151; --t3:#7c8fa3;
  --accent:#4f52cc; --accent2:#6366f1;
  --glow:rgba(99,102,241,.2);
  --gdim:rgba(22,163,74,.12); --ydim:rgba(202,138,4,.12);
  --rdim:rgba(220,38,38,.12); --bdim:rgba(59,130,246,.12);
  --green:#15803d; --yellow:#b45309; --red:#dc2626; --blue:#2563eb;
}
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
html{scroll-behavior:smooth}
body{
  font-family:'Inter',system-ui,-apple-system,sans-serif;
  background:var(--bg);color:var(--text);font-size:13.5px;line-height:1.5;min-height:100vh;
}
[data-theme="dark"] body{
  background-image:radial-gradient(ellipse 80% 40% at 50% -10%,rgba(99,102,241,.13),transparent);
}

/* ── Header ── */
header{
  position:sticky;top:0;z-index:200;height:60px;
  display:flex;align-items:center;gap:16px;padding:0 24px;
  background:rgba(7,9,15,.75);backdrop-filter:blur(20px) saturate(160%);
  border-bottom:1px solid var(--border);
}
[data-theme="light"] header{background:rgba(241,243,249,.92);box-shadow:0 1px 0 var(--border)}
.logo{
  display:flex;align-items:center;gap:10px;font-weight:700;font-size:15px;
  white-space:nowrap;text-decoration:none;
  background:linear-gradient(135deg,#818cf8,#c084fc);
  -webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;
}
.logo-box{
  width:32px;height:32px;border-radius:9px;flex-shrink:0;
  background:linear-gradient(135deg,#6366f1,#8b5cf6);
  display:flex;align-items:center;justify-content:center;font-size:16px;
  -webkit-text-fill-color:initial;box-shadow:0 0 16px rgba(99,102,241,.4);
}
.search-wrap{flex:1;max-width:500px;position:relative}
.search-wrap svg{position:absolute;left:12px;top:50%;transform:translateY(-50%);color:var(--t3);pointer-events:none}
#search{
  width:100%;padding:9px 12px 9px 38px;background:var(--surface2);
  border:1px solid var(--border);border-radius:var(--rs);
  color:var(--text);font-family:inherit;font-size:13px;outline:none;
  transition:border-color .2s,box-shadow .2s;
}
#search:focus{border-color:var(--accent);box-shadow:0 0 0 3px var(--glow)}
#search::placeholder{color:var(--t3)}
.hbar{margin-left:auto;display:flex;gap:8px;align-items:center}

/* ── Buttons ── */
.btn{
  display:inline-flex;align-items:center;gap:6px;padding:7px 14px;
  border-radius:var(--rs);font-family:inherit;font-size:12px;font-weight:500;
  cursor:pointer;border:1px solid var(--border);background:var(--surface);
  color:var(--t2);transition:border-color .15s,color .15s,background .15s;white-space:nowrap;
}
.btn:hover{border-color:var(--border2);color:var(--text);background:var(--surface2)}

/* ── Toggle ── */
.mask-row{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--t2);cursor:pointer;user-select:none}
.sw{width:34px;height:20px;background:var(--border2);border-radius:99px;position:relative;transition:background .2s;flex-shrink:0}
.sw::after{content:'';position:absolute;width:16px;height:16px;background:#fff;border-radius:50%;top:2px;left:2px;transition:left .2s;box-shadow:0 1px 4px rgba(0,0,0,.3)}
.sw.on{background:var(--accent)}
.sw.on::after{left:16px}

/* ── Page ── */
.page{max-width:1240px;margin:0 auto;padding:28px 24px 72px}

/* ── Stat cards ── */
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:28px}
@media(max-width:720px){.stats{grid-template-columns:repeat(2,1fr)}}
.sc{
  background:var(--surface);border:1px solid var(--border);border-radius:var(--r);
  padding:20px;position:relative;overflow:hidden;backdrop-filter:blur(10px);
  transition:border-color .2s,transform .2s;
}
.sc:hover{border-color:var(--border2);transform:translateY(-1px)}
.sc::after{content:'';position:absolute;top:0;left:0;right:0;height:2px;border-radius:var(--r) var(--r) 0 0}
.sc.g::after{background:linear-gradient(90deg,var(--green),transparent)}
.sc.r::after{background:linear-gradient(90deg,var(--red),transparent)}
.sc.y::after{background:linear-gradient(90deg,var(--yellow),transparent)}
.sc.b::after{background:linear-gradient(90deg,var(--blue),transparent)}
.sc-icon{width:38px;height:38px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:18px;margin-bottom:16px}
.sc.g .sc-icon{background:var(--gdim)} .sc.r .sc-icon{background:var(--rdim)}
.sc.y .sc-icon{background:var(--ydim)} .sc.b .sc-icon{background:var(--bdim)}
.sc-num{font-size:30px;font-weight:700;line-height:1;margin-bottom:5px}
.sc.g .sc-num{color:var(--green)} .sc.r .sc-num{color:var(--red)}
.sc.y .sc-num{color:var(--yellow)} .sc.b .sc-num{color:var(--blue)}
.sc-lbl{font-size:11px;color:var(--t3);text-transform:uppercase;letter-spacing:.6px;font-weight:500}

/* ── Toolbar ── */
.toolbar{display:flex;align-items:center;gap:10px;margin-bottom:18px;flex-wrap:wrap}
.chips{display:flex;gap:6px;flex-wrap:wrap;align-items:center;flex:1}
.chip{
  padding:5px 13px;border-radius:99px;font-size:12px;font-weight:500;cursor:pointer;
  border:1px solid var(--border);background:transparent;color:var(--t2);
  transition:all .15s;white-space:nowrap;
}
.chip:hover{border-color:var(--accent);color:var(--accent2)}
.chip.active{background:rgba(99,102,241,.15);border-color:var(--accent);color:var(--accent2)}
.ts{font-size:11px;color:var(--t3);white-space:nowrap}

/* ── Group ── */
.group{
  background:var(--surface);border:1px solid var(--border);
  border-radius:var(--r);margin-bottom:10px;overflow:hidden;
  transition:border-color .2s;backdrop-filter:blur(8px);
}
.group:hover{border-color:var(--border2)}
.gh{display:flex;align-items:center;gap:10px;padding:13px 18px;cursor:pointer;user-select:none}
.gh:hover .g-title{color:var(--accent2)}
.g-icon{font-size:15px;width:22px;text-align:center;flex-shrink:0}
.g-title{font-weight:600;font-size:13px;transition:color .15s}
.g-cnt{
  font-size:11px;padding:2px 9px;border-radius:99px;
  background:var(--surface2);color:var(--t3);border:1px solid var(--border);font-weight:500;
}
.g-line{flex:1;height:1px;background:var(--border);margin:0 6px}
.arrow{color:var(--t3);transition:transform .25s;font-size:10px;flex-shrink:0}
.group.closed .arrow{transform:rotate(-90deg)}
.group.closed .gb{display:none}
.gb{border-top:1px solid var(--border)}

/* ── Table ── */
table{width:100%;border-collapse:collapse}
thead{position:sticky;top:60px;z-index:10}
th{
  padding:8px 16px;text-align:left;font-size:10px;font-weight:600;color:var(--t3);
  text-transform:uppercase;letter-spacing:.6px;
  background:var(--solid);border-bottom:1px solid var(--border);
}
[data-theme="dark"] th{background:#0a0d15;color:#6b7a96}
[data-theme="light"] th{background:#f8f9fc;border-bottom:1px solid var(--border)}
td{padding:10px 16px;border-bottom:1px solid var(--border);vertical-align:middle}
tr:last-child td{border-bottom:none}
tbody tr{transition:background .1s}
tbody tr:hover td{background:var(--surface2)}

/* ── Key ── */
.key{
  font-family:'JetBrains Mono','Fira Code',monospace;
  font-size:12px;font-weight:500;color:var(--accent2);word-break:break-all;
}

/* ── Value ── */
.val{
  font-family:'JetBrains Mono','Fira Code',monospace;
  font-size:12px;color:var(--t2);max-width:480px;word-break:break-all;
  max-height:38px;overflow:hidden;position:relative;cursor:pointer;
}
[data-theme="light"] .val{color:#374151}
.val.open{max-height:600px}
.val.long:not(.open)::after{
  content:'';position:absolute;bottom:0;left:0;right:0;height:22px;
  background:linear-gradient(transparent,var(--solid));pointer-events:none;
}
[data-theme="dark"] tbody tr:hover .val.long:not(.open)::after{background:linear-gradient(transparent,rgba(14,17,23,.95))}
[data-theme="light"] tbody tr:hover .val.long:not(.open)::after{background:linear-gradient(transparent,rgba(248,249,252,.95))}
[data-theme="light"] .val.long:not(.open)::after{background:linear-gradient(transparent,#fff)}

/* ── Badge ── */
.bs{
  display:inline-flex;align-items:center;gap:4px;font-size:10px;padding:3px 8px;
  border-radius:99px;background:var(--rdim);color:var(--red);
  border:1px solid rgba(248,113,113,.2);white-space:nowrap;font-weight:500;
}

/* ── Copy ── */
.cp{
  opacity:0;padding:4px 11px;font-size:11px;font-weight:500;border-radius:6px;
  cursor:pointer;font-family:inherit;border:1px solid var(--border);
  background:var(--surface2);color:var(--t2);
  transition:opacity .15s,background .15s,color .15s,border-color .15s;white-space:nowrap;
}
tbody tr:hover .cp{opacity:1}
.cp:hover{background:var(--accent);border-color:var(--accent);color:#fff}
.cp.ok{background:var(--green);border-color:var(--green);color:#fff;opacity:1}

/* ── Empty ── */
.empty{text-align:center;padding:80px 20px;color:var(--t3)}
.empty-ic{font-size:44px;margin-bottom:12px;opacity:.6}
.empty-tx{font-size:14px}

/* ── Toast ── */
#toast{
  position:fixed;bottom:24px;right:24px;z-index:9999;
  background:var(--solid);border:1px solid var(--border2);
  padding:10px 18px;border-radius:10px;font-size:13px;color:var(--text);
  box-shadow:0 8px 32px rgba(0,0,0,.18);
  transform:translateY(80px);opacity:0;
  transition:transform .3s cubic-bezier(.34,1.56,.64,1),opacity .25s;
}
[data-theme="dark"] #toast{box-shadow:0 8px 32px rgba(0,0,0,.5)}
#toast.show{transform:translateY(0);opacity:1}

/* ── PIN Modal ── */
#pin-overlay{
  display:none;position:fixed;inset:0;z-index:1000;
  background:rgba(0,0,0,.65);backdrop-filter:blur(6px);
  align-items:center;justify-content:center;
}
#pin-overlay.open{display:flex}
#pin-card{
  background:var(--solid);border:1px solid var(--border2);
  border-radius:16px;padding:32px 28px;width:340px;
  box-shadow:0 24px 64px rgba(0,0,0,.6);
  animation:pop .25s cubic-bezier(.34,1.56,.64,1);
}
@keyframes pop{from{opacity:0;transform:scale(.92)}to{opacity:1;transform:scale(1)}}
.pin-icon{
  width:48px;height:48px;border-radius:13px;margin:0 auto 18px;
  background:linear-gradient(135deg,#6366f1,#8b5cf6);
  display:flex;align-items:center;justify-content:center;font-size:22px;
  box-shadow:0 0 20px rgba(99,102,241,.4);
}
#pin-card h2{font-size:16px;font-weight:600;text-align:center;margin-bottom:6px}
#pin-card p{font-size:12px;color:var(--t2);text-align:center;margin-bottom:22px;line-height:1.5}
#pin-input{
  width:100%;padding:10px 14px;background:var(--surface2);
  border:1px solid var(--border);border-radius:var(--rs);
  color:var(--text);font-family:inherit;font-size:14px;
  text-align:center;letter-spacing:4px;outline:none;
  transition:border-color .2s,box-shadow .2s;margin-bottom:10px;
}
#pin-input:focus{border-color:var(--accent);box-shadow:0 0 0 3px var(--glow)}
#pin-input.err{border-color:var(--red);box-shadow:0 0 0 3px var(--rdim);animation:shake .3s}
@keyframes shake{0%,100%{transform:translateX(0)}25%{transform:translateX(-6px)}75%{transform:translateX(6px)}}
#pin-err{font-size:11px;color:var(--red);text-align:center;min-height:16px;margin-bottom:12px}
.pin-btns{display:flex;gap:8px}
#pin-confirm{
  flex:1;padding:10px;border-radius:var(--rs);font-family:inherit;
  font-size:13px;font-weight:600;cursor:pointer;border:none;
  background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff;
  transition:opacity .15s;
}
#pin-confirm:hover{opacity:.88}
#pin-cancel{
  padding:10px 16px;border-radius:var(--rs);font-family:inherit;
  font-size:13px;cursor:pointer;border:1px solid var(--border);
  background:var(--surface2);color:var(--t2);transition:background .15s;
}
#pin-cancel:hover{background:var(--border)}

/* ── Lock badge ── */
#lock-badge{
  display:inline-flex;align-items:center;gap:6px;
  padding:5px 11px;border-radius:99px;font-size:11px;font-weight:500;
  border:1px solid var(--border);cursor:pointer;transition:all .2s;
  color:var(--red);background:var(--rdim);border-color:rgba(248,113,113,.25);
}
#lock-badge.unlocked{color:var(--green);background:var(--gdim);border-color:rgba(52,211,153,.25)}
#lock-badge svg{flex-shrink:0}
</style>
</head>
<body>

<header>
  <div class="logo">
    <div class="logo-box">⚙️</div>
    Envision
  </div>
  <div class="search-wrap">
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
      <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
    </svg>
    <input id="search" type="text" placeholder="Variable ara…  (örn: JAVA_HOME, PATH, NODE)" autocomplete="off" spellcheck="false"/>
  </div>
  <div class="hbar">
    <label class="mask-row btn" style="cursor:pointer">
      <div class="sw on" id="sw"></div>
      <span>Maskele</span>
    </label>
    <div id="lock-badge" onclick="onLockClick()" title="Oturum kilit durumu">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
        <path d="M7 11V7a5 5 0 0110 0v4"/>
      </svg>
      <span id="lock-label">Kilitli</span>
    </div>
    <button class="btn" onclick="exportJson()">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
        <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
      </svg>
      JSON
    </button>
    <button class="btn" onclick="toggleTheme()" id="themeBtn">🌙</button>
  </div>
</header>

<!-- PIN Modal -->
<div id="pin-overlay">
  <div id="pin-card">
    <div class="pin-icon">🔐</div>
    <h2>Yetkilendirme Gerekli</h2>
    <p>Bu hassas değeri kopyalamak için<br>PIN kodunu girin.</p>
    <input id="pin-input" type="password" placeholder="● ● ● ●" maxlength="32"
           autocomplete="off" onkeydown="if(event.key==='Enter')confirmPin()"/>
    <div id="pin-err"></div>
    <div class="pin-btns">
      <button id="pin-cancel" onclick="closePin()">İptal</button>
      <button id="pin-confirm" onclick="confirmPin()">Onayla</button>
    </div>
  </div>
</div>

<div class="page">
  <div class="stats">
    <div class="sc g">
      <div class="sc-icon">📦</div>
      <div class="sc-num" id="sTotal">{{TOTAL}}</div>
      <div class="sc-lbl">Toplam</div>
    </div>
    <div class="sc r">
      <div class="sc-icon">🔒</div>
      <div class="sc-num" id="sSens">{{SENSITIVE}}</div>
      <div class="sc-lbl">Hassas</div>
    </div>
    <div class="sc y">
      <div class="sc-icon">🗂️</div>
      <div class="sc-num" id="sCat">{{CATEGORIES}}</div>
      <div class="sc-lbl">Kategori</div>
    </div>
    <div class="sc b">
      <div class="sc-icon">👁</div>
      <div class="sc-num" id="sVis">{{TOTAL}}</div>
      <div class="sc-lbl">Gösterilen</div>
    </div>
  </div>

  <div class="toolbar">
    <div class="chips" id="chips">
      <div class="chip active" data-cat="ALL" onclick="setFilter(this)">Tümü</div>
    </div>
    <span class="ts">🕐 {{GENERATED}}</span>
  </div>

  <div id="main"></div>
</div>
<div id="toast"></div>

<script>
const RAW       = {{JSON_DATA}};
const PIN_HASH  = "{{PIN_HASH}}";   // boş → PIN koruması yok
const PIN_ON    = PIN_HASH !== "";

const ICONS={JAVA:'☕',MAVEN:'🔨',PATH:'🛣️',PYTHON:'🐍',NODE:'⬡',
             DOCKER:'🐳',CLOUD:'☁️',DATABASE:'🗄️',SYSTEM:'💻',OTHER:'📦'};

let masked=true, activeCat='ALL', query='';
let sessionUnlocked=!PIN_ON;  // PIN yoksa baştan açık
let pendingVal=null;           // onay bekleyen kopyalanacak değer
let pendingUnmask=false;       // PIN onayı sonrası maskelemeyi kaldır

/* ── Kilit badge ── */
function syncLockBadge(){
  const b=document.getElementById('lock-badge');
  const l=document.getElementById('lock-label');
  if(!PIN_ON){ b.style.display='none'; return; }
  if(sessionUnlocked){
    b.classList.add('unlocked');
    l.textContent='Yetkili';
    b.title='Oturumu kilitle';
  } else {
    b.classList.remove('unlocked');
    l.textContent='Kilitli';
    b.title='PIN ile aç';
  }
}
function onLockClick(){
  if(!PIN_ON) return;
  if(sessionUnlocked){ sessionUnlocked=false; syncLockBadge(); render(); toast('🔒 Oturum kilitlendi'); }
  else { openPin(null); }
}

/* ── PIN Modal ── */
function openPin(val){
  pendingVal=val;
  document.getElementById('pin-input').value='';
  document.getElementById('pin-err').textContent='';
  document.getElementById('pin-input').classList.remove('err');
  document.getElementById('pin-overlay').classList.add('open');
  setTimeout(()=>document.getElementById('pin-input').focus(),80);
}
function closePin(){
  document.getElementById('pin-overlay').classList.remove('open');
  pendingVal=null;
  pendingUnmask=false;
}
async function confirmPin(){
  const input=document.getElementById('pin-input');
  const err=document.getElementById('pin-err');
  const pin=input.value;
  if(!pin){ err.textContent='PIN boş bırakılamaz.'; return; }

  const hash=await sha256(pin);
  if(hash!==PIN_HASH){
    input.classList.add('err');
    err.textContent='Hatalı PIN. Tekrar deneyin.';
    input.value='';
    setTimeout(()=>input.classList.remove('err'),600);
    return;
  }

  sessionUnlocked=true;
  syncLockBadge();
  const doUnmask=pendingUnmask;
  const doCopy=pendingVal;
  closePin(); // pendingVal ve pendingUnmask sıfırlanır
  toast('🔓 Yetkilendirme başarılı');
  render();

  if(doCopy!==null){
    navigator.clipboard.writeText(doCopy).then(()=>toast('📋 Panoya kopyalandı'));
  }
  if(doUnmask){
    masked=false;
    document.getElementById('sw').classList.toggle('on',masked);
    render();
  }
}
async function sha256(text){
  const buf=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf)).map(b=>b.toString(16).padStart(2,'0')).join('');
}

/* ── Render ── */
const chipsEl=document.getElementById('chips');
RAW.forEach(g=>{
  const d=document.createElement('div');
  d.className='chip'; d.dataset.cat=g.category;
  d.onclick=function(){setFilter(this)};
  d.innerHTML=(ICONS[g.category]||'📦')+' '+g.category+
    ' <span style="opacity:.45;font-size:10px">'+g.items.length+'</span>';
  chipsEl.appendChild(d);
});

function render(){
  const q=query.toLowerCase();
  const main=document.getElementById('main');
  let vis=0, html='';
  RAW.forEach(g=>{
    if(activeCat!=='ALL'&&g.category!==activeCat) return;
    const items=g.items.filter(i=>!q||i.key.toLowerCase().includes(q)||i.value.toLowerCase().includes(q));
    if(!items.length) return;
    vis+=items.length;
    const icon=ICONS[g.category]||'📦';
    const rows=items.map(i=>{
      const dv=masked&&i.sensitive
        ?(i.value.length<=4?'●●●●●●':i.value.slice(0,2)+'●●●●'+i.value.slice(-2))
        :i.value;
      const long=dv.length>90;
      /* Hassas + kilitli ise kopyala butonu engellenir */
      const blocked=PIN_ON&&i.sensitive&&!sessionUnlocked;
      const cpBtn=blocked
        ?`<button class="cp" style="opacity:1;color:var(--red);border-color:rgba(248,113,113,.3)" onclick='cp(this,${JSON.stringify(i.value)},true)'>🔒 PIN</button>`
        :`<button class="cp" onclick='cp(this,${JSON.stringify(i.value)},${i.sensitive})'>Kopyala</button>`;
      return `<tr>
        <td><span class="key">${esc(i.key)}</span></td>
        <td><div class="val${long?' long':''}" onclick="this.classList.toggle('open')">${esc(dv||'(boş)')}</div></td>
        <td>${i.sensitive?'<span class="bs">🔒 hassas</span>':''}</td>
        <td>${cpBtn}</td>
      </tr>`;
    }).join('');
    html+=`<div class="group" id="g-${g.category}">
      <div class="gh" onclick="toggleGrp('g-${g.category}')">
        <span class="g-icon">${icon}</span>
        <span class="g-title">${g.category}</span>
        <span class="g-cnt">${items.length}</span>
        <span class="g-line"></span>
        <span class="arrow">▼</span>
      </div>
      <div class="gb">
        <table>
          <thead><tr>
            <th style="width:240px">Variable</th><th>Değer</th>
            <th style="width:95px">Durum</th><th style="width:80px"></th>
          </tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    </div>`;
  });
  main.innerHTML=html||`<div class="empty"><div class="empty-ic">🔍</div><div class="empty-tx">Sonuç bulunamadı</div></div>`;
  document.getElementById('sVis').textContent=vis;
}

function toggleGrp(id){document.getElementById(id).classList.toggle('closed')}
function setFilter(el){
  activeCat=el.dataset.cat;
  document.querySelectorAll('.chip').forEach(c=>c.classList.remove('active'));
  el.classList.add('active'); render();
}

function cp(btn,val,sensitive){
  if(PIN_ON&&sensitive&&!sessionUnlocked){ openPin(val); return; }
  navigator.clipboard.writeText(val).then(()=>{
    btn.textContent='✓ Kopyalandı'; btn.classList.add('ok');
    setTimeout(()=>{btn.textContent='Kopyala';btn.classList.remove('ok')},1500);
  });
  toast('📋 Panoya kopyalandı');
}

function exportJson(){
  const b=new Blob([JSON.stringify(RAW,null,2)],{type:'application/json'});
  const a=document.createElement('a'); a.href=URL.createObjectURL(b);
  a.download='env-variables.json'; a.click();
  toast('⬇️ JSON indiriliyor');
}
function toggleTheme(){
  const h=document.documentElement, dark=h.dataset.theme==='dark';
  h.dataset.theme=dark?'light':'dark';
  document.getElementById('themeBtn').textContent=dark?'☀️':'🌙';
}
document.getElementById('search').addEventListener('input',e=>{query=e.target.value;render()});
document.getElementById('sw').addEventListener('click',function(){
  // Maskelemeyi kaldırmak isteniyor ama oturum kilitli → PIN iste
  if(masked && PIN_ON && !sessionUnlocked){
    pendingUnmask=true;
    openPin(null);
    return;
  }
  masked=!masked; this.classList.toggle('on',masked); render();
});
document.getElementById('pin-overlay').addEventListener('click',function(e){
  if(e.target===this) closePin();
});
function toast(msg){
  const t=document.getElementById('toast'); t.textContent=msg;
  t.classList.add('show'); setTimeout(()=>t.classList.remove('show'),2400);
}
function esc(s){
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

syncLockBadge();
render();
</script>
</body>
</html>
""";
}
