package com.evkumanda.app.tv

val PANEL_HTML: String = """<!DOCTYPE html>
<html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ev TV Panel</title>
<style>
:root{--bg:#0b0f1a;--card:rgba(255,255,255,.07);--line:rgba(255,255,255,.12);--mut:#9aa3bd;--acc:#8b6cff}
*{box-sizing:border-box}
body{margin:0;font-family:-apple-system,BlinkMacSystemFont,"SF Pro Display","Segoe UI",Roboto,sans-serif;
background:radial-gradient(1200px 700px at 10% -10%,#2a1b5e 0%,transparent 60%),radial-gradient(900px 600px at 100% 0%,#0f3a52 0%,transparent 55%),var(--bg);
color:#fff;min-height:100vh}
.wrap{max-width:880px;margin:0 auto;padding:28px 18px 120px}
h1{font-size:34px;font-weight:700;margin:6px 0 4px;letter-spacing:-.5px}
.sub{color:var(--mut);margin-bottom:22px}
.card{background:var(--card);border:1px solid var(--line);border-radius:26px;padding:20px;margin:16px 0;backdrop-filter:blur(18px)}
h2{font-size:19px;margin:0 0 4px}
.hint{color:var(--mut);font-size:13px;margin:0 0 14px}
label{display:block;font-size:12px;color:var(--mut);margin:10px 0 4px}
input,select{width:100%;padding:12px 14px;border-radius:14px;border:1px solid var(--line);background:rgba(0,0,0,.35);color:#fff;font-size:15px}
input[type=color]{padding:2px;height:44px}
input[type=checkbox]{width:auto}
.row{display:flex;gap:10px;flex-wrap:wrap}.row>*{flex:1;min-width:140px}
button{border:0;border-radius:14px;padding:12px 18px;font-size:15px;font-weight:600;color:#fff;background:var(--acc);cursor:pointer}
button.ghost{background:rgba(255,255,255,.1)}button.red{background:#c0392b}
.hero{border:1px solid var(--line);border-radius:18px;padding:12px;margin:10px 0;background:rgba(0,0,0,.25)}
.thumbs{display:flex;gap:10px;flex-wrap:wrap;margin-top:10px}
.th{position:relative;width:120px;height:68px;border-radius:12px;overflow:hidden;background:#000;border:1px solid var(--line)}
.th img{width:100%;height:100%;object-fit:cover}.th span{position:absolute;left:4px;bottom:3px;font-size:10px;background:#000a;border-radius:6px;padding:1px 5px}
.th b{position:absolute;right:3px;top:3px;background:#000a;border-radius:50%;width:22px;height:22px;text-align:center;line-height:22px;cursor:pointer}
.apps{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:6px;max-height:260px;overflow:auto}
.apps label{display:flex;gap:8px;align-items:center;margin:0;color:#fff;font-size:14px;padding:6px 8px;border-radius:10px;background:rgba(255,255,255,.05)}
.bar{position:fixed;left:0;right:0;bottom:0;padding:14px;background:linear-gradient(transparent,#0b0f1a 40%);display:flex;justify-content:center;gap:12px}
.prog{height:6px;background:#fff2;border-radius:6px;overflow:hidden;margin-top:8px;display:none}.prog i{display:block;height:100%;width:0;background:var(--acc)}
.toast{position:fixed;top:14px;left:50%;transform:translateX(-50%);background:#1d2540;border:1px solid var(--line);padding:10px 18px;border-radius:14px;display:none}
</style></head><body><div class="wrap">
<h1>Ev TV Panel</h1><div class="sub">TV arayüzünü buradan özelleştir. Değişiklikler "Kaydet" ile TV'ye anında uygulanır.</div>

<div class="card"><h2>Genel</h2><p class="hint">Karşılama mesajı ve vurgu rengi.</p>
<div class="row"><div><label>Adın</label><input id="userName"></div><div><label>Vurgu rengi</label><input id="accent" type="color"></div></div></div>

<div class="card"><h2>Ana ekran afişleri</h2><p class="hint">Büyük afiş görselleri (16:9 yatay en iyisi). Sırayla döner. İstersen bir afişe uygulama bağla, tıklayınca o açılır.</p>
<div class="hint" id="free"></div><label>Ana ekran arka plan videosu (seçilirse afiş görselleri yerine döngüde oynar)</label><select id="homeVideo"></select>
<div class="row" style="margin:8px 0 14px"><button class="ghost" onclick="pick('vid-')">Video yükle (mp4)</button></div>
<div id="heroes"></div>
<div class="row"><button class="ghost" onclick="addHero()">+ Afiş ekle</button>
<button class="ghost" onclick="pick('hero-')">Afiş görseli yükle</button></div>
<div class="thumbs" id="heroImgs"></div></div>

<div class="card"><h2>Kısayol uygulamalar</h2><p class="hint">Ana ekrandaki kartlar (Play Store, Dosyalar ve Ayarlar her zaman vardır). Seçmezsen varsayılanlar gösterilir.</p>
<div class="apps" id="apps"></div></div>

<div class="card"><h2>Ekran koruyucu</h2><p class="hint">Boşta kalınca (ve Android ekran koruyucu olarak seçilirse) oynar.</p>
<div class="row"><div><label>Mod</label><select id="saverMode">
<option value="aurora">Aurora (akışkan renkler)</option><option value="stars">Yıldızlı gökyüzü</option><option value="snow">Kar yağışı</option>
<option value="images">Fotoğraf (yavaş zoom)</option><option value="video">Video (döngü)</option></select></div>
<div><label>Boşta kalma süresi (dk, 0 = kapalı)</label><input id="idleMin" type="number" min="0"></div>
<div><label>Fotoğraf süresi (sn)</label><input id="saverInterval" type="number" min="4"></div></div>
<label><input type="checkbox" id="saverClock"> Saat göster</label>
<label>Video seç</label><select id="saverVideo"></select>
<div class="row" style="margin-top:12px"><button class="ghost" onclick="pick('wall-')">Fotoğraf yükle</button>
<button class="ghost" onclick="pick('vid-')">Video yükle (mp4)</button></div>
<div class="thumbs" id="wallImgs"></div><div class="prog" id="prog"><i id="progi"></i></div></div>

<div class="card"><h2>Hava durumu ve geri sayım</h2>
<div class="row"><div><label>Şehir ara</label><input id="cityQ" placeholder="Şehir yaz, Enter"></div></div>
<div id="cityRes" class="row" style="margin-top:8px"></div>
<div class="row"><div><label>Seçili şehir</label><input id="city"></div><div><label>Enlem</label><input id="lat"></div><div><label>Boylam</label><input id="lon"></div></div>
<div class="row"><div><label>Geri sayım başlığı</label><input id="cdTitle" placeholder="ör. Tatil"></div><div><label>Tarih</label><input id="cdDate" type="date"></div></div></div>

<div class="card"><h2>Ev kontrol</h2><p class="hint">TV ana ekranındaki hızlı butonlar için.</p>
<div class="row"><div><label>PC IP</label><input id="pcIp"></div><div><label>PC MAC</label><input id="pcMac"></div></div>
<div class="row"><div><label>PS5 IP</label><input id="ps5Ip"></div><div><label>PS5 user-credential</label><input id="ps5Cred"></div></div></div>
</div>
<div class="bar"><button onclick="save()">Kaydet</button></div>
<div class="toast" id="toast"></div>
<input type="file" id="file" style="display:none" multiple>
<script>
var S={cfg:{},media:[],apps:[]};
function g(id){return document.getElementById(id)}
function toast(t){var e=g('toast');e.textContent=t;e.style.display='block';setTimeout(function(){e.style.display='none'},2400)}
function load(){fetch('/api/state').then(function(r){return r.json()}).then(function(j){S=j;render()})}
function isImg(n){return /\.(jpe?g|png|webp|gif)${'$'}/i.test(n)}
function render(){
 var c=S.cfg;
 ['userName','accent','idleMin','saverInterval','city','lat','lon','cdTitle','cdDate','pcIp','pcMac','ps5Ip','ps5Cred'].forEach(function(k){g(k).value=c[k]==null?'':c[k]});
 g('saverMode').value=c.saverMode;g('saverClock').checked=!!c.saverClock;
 ['saverVideo','homeVideo'].forEach(function(id){
  var vs=g(id);vs.innerHTML='<option value="">(yok)</option>';
  S.media.filter(function(m){return /^vid-/.test(m.name)}).forEach(function(m){var o=document.createElement('option');o.value=m.name;o.textContent=m.name;vs.appendChild(o)});
  vs.value=c[id]||'';
 });
 if(S.free!=null)g('free').textContent='TV boş alan: '+(S.free/1e9).toFixed(1)+' GB. 4K için H.265 (HEVC) 3840x2160, 30 fps önerilir.';
 renderHeroes();renderThumbs();renderApps();
}
function thumbHtml(m){return '<div class="th"><img src="/media/'+encodeURIComponent(m.name)+'"><span>'+m.name.slice(0,18)+'</span><b onclick="del(\''+m.name+'\')">×</b></div>'}
function renderThumbs(){
 g('heroImgs').innerHTML=S.media.filter(function(m){return /^hero-/.test(m.name)&&isImg(m.name)}).map(thumbHtml).join('');
 g('wallImgs').innerHTML=S.media.filter(function(m){return /^wall-/.test(m.name)&&isImg(m.name)}).map(thumbHtml).join('')+
  S.media.filter(function(m){return /^vid-/.test(m.name)}).map(function(m){return '<div class="th"><span>'+m.name.slice(0,18)+'</span><b onclick="del(\''+m.name+'\')">×</b></div>'}).join('');
}
function renderHeroes(){
 var imgs=S.media.filter(function(m){return /^hero-/.test(m.name)&&isImg(m.name)});
 var h='';
 (S.cfg.heroes||[]).forEach(function(x,i){
  var io='<option value="">(görsel yok)</option>'+imgs.map(function(m){return '<option'+(m.name==x.image?' selected':'')+'>'+m.name+'</option>'}).join('');
  var ao='<option value="">(uygulama yok)</option>'+S.apps.map(function(a){return '<option value="'+a.pkg+'"'+(a.pkg==x.pkg?' selected':'')+'>'+a.label+'</option>'}).join('');
  h+='<div class="hero"><div class="row"><div><label>Görsel</label><select onchange="S.cfg.heroes['+i+'].image=this.value">'+io+'</select></div>'+
  '<div><label>Uygulama</label><select onchange="S.cfg.heroes['+i+'].pkg=this.value">'+ao+'</select></div></div>'+
  '<div class="row"><div><label>Başlık</label><input value="'+(x.title||'').replace(/"/g,'&quot;')+'" oninput="S.cfg.heroes['+i+'].title=this.value"></div>'+
  '<div><label>Alt başlık</label><input value="'+(x.subtitle||'').replace(/"/g,'&quot;')+'" oninput="S.cfg.heroes['+i+'].subtitle=this.value"></div></div>'+
  '<div style="margin-top:10px"><button class="red" onclick="S.cfg.heroes.splice('+i+',1);renderHeroes()">Sil</button></div></div>';
 });
 g('heroes').innerHTML=h;
}
function addHero(){S.cfg.heroes=S.cfg.heroes||[];S.cfg.heroes.push({image:'',title:'',subtitle:'',pkg:''});renderHeroes()}
function renderApps(){
 var p=S.cfg.pinned||[];
 g('apps').innerHTML=S.apps.map(function(a){return '<label><input type="checkbox" '+(p.indexOf(a.pkg)>=0?'checked ':'')+'onchange="togglePin(\''+a.pkg+'\',this.checked)">'+a.label+'</label>'}).join('');
}
function togglePin(p,on){var l=S.cfg.pinned||[];var i=l.indexOf(p);if(on&&i<0)l.push(p);if(!on&&i>=0)l.splice(i,1);S.cfg.pinned=l}
var curPrefix='';
function pick(pref){curPrefix=pref;var f=g('file');f.accept=pref=='vid-'?'video/*':'image/*';f.value='';f.click()}
g('file').onchange=function(){
 var files=Array.prototype.slice.call(this.files);var i=0;
 function next(){
  if(i>=files.length){toast('Yüklendi');load();g('prog').style.display='none';return}
  var f=files[i++];var x=new XMLHttpRequest();
  x.open('PUT','/api/upload?name='+encodeURIComponent(curPrefix+f.name));
  g('prog').style.display='block';
  x.upload.onprogress=function(e){if(e.lengthComputable)g('progi').style.width=(100*e.loaded/e.total)+'%'};
  x.onload=next;x.onerror=function(){toast('Yükleme hatası')};
  x.send(f);
 }
 next();
};
function del(n){if(!confirm(n+' silinsin mi?'))return;fetch('/api/delete?name='+encodeURIComponent(n),{method:'POST'}).then(load)}
g('cityQ').onkeydown=function(e){
 if(e.key!='Enter')return;
 fetch('https://geocoding-api.open-meteo.com/v1/search?count=5&language=tr&name='+encodeURIComponent(this.value)).then(function(r){return r.json()}).then(function(j){
  var box=g('cityRes');box.innerHTML='';
  (j.results||[]).forEach(function(r){var b=document.createElement('button');b.className='ghost';b.textContent=r.name+(r.admin1?', '+r.admin1:'')+', '+r.country_code;
   b.onclick=function(){g('city').value=r.name;g('lat').value=r.latitude;g('lon').value=r.longitude;box.innerHTML=''};box.appendChild(b)});
  if(!(j.results||[]).length)toast('Şehir bulunamadı');
 }).catch(function(){toast('Şehir araması için internet gerekli')});
};
function save(){
 var c=S.cfg;
 ['userName','accent','city','cdTitle','cdDate','pcIp','pcMac','ps5Ip','ps5Cred'].forEach(function(k){c[k]=g(k).value});
 c.idleMin=parseInt(g('idleMin').value||'0');c.saverInterval=parseInt(g('saverInterval').value||'12');
 c.lat=parseFloat(g('lat').value||'0');c.lon=parseFloat(g('lon').value||'0');
 c.saverMode=g('saverMode').value;c.saverClock=g('saverClock').checked;c.saverVideo=g('saverVideo').value;c.homeVideo=g('homeVideo').value;
 fetch('/api/config',{method:'POST',body:JSON.stringify(c)}).then(function(r){return r.text()}).then(function(t){toast(t=='ok'?'Kaydedildi ✓':t)});
}
load();
</script></body></html>"""
