(function () {
  if (!window.DeenDaily || window.__ddNative) return;
  window.__ddNative = true;

  /* ---- Notification: Android WebView-তে নেই, তাই native দিয়ে ---- */
  var waiting = [];
  function Notif(title, opts) {
    opts = opts || {};
    if (window.__ddScheduled && String(opts.tag || '').indexOf('deen-daily-') === 0) return;
    try {
      window.DeenDaily.showNotification(String(title), String(opts.body || ''), String(opts.tag || title));
    } catch (e) {}
  }
  Object.defineProperty(Notif, 'permission', {
    get: function () {
      try { return window.DeenDaily.notifPermission(); } catch (e) { return 'denied'; }
    }
  });
  Notif.requestPermission = function (cb) {
    return new Promise(function (resolve) {
      waiting.push(function (p) {
        if (typeof cb === 'function') cb(p);
        resolve(p);
      });
      try {
        window.DeenDaily.requestNotifPermission();
      } catch (e) {
        var f = waiting.pop();
        if (f) f('denied');
      }
    });
  };
  window.ddOnNotifPermission = function (p) {
    var list = waiting.splice(0);
    for (var i = 0; i < list.length; i++) list[i](p);
  };
  try { window.Notification = Notif; } catch (e) {}

  /* ---- blob ডাউনলোড (যেমন ব্যাকআপ ফাইল): native দিয়ে Downloads-এ সেভ ---- */
  var origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    var a = this;
    if (a.download && a.href && a.href.indexOf('blob:') === 0) {
      fetch(a.href).then(function (r) { return r.blob(); }).then(function (b) {
        var fr = new FileReader();
        fr.onload = function () {
          var s = String(fr.result || '');
          window.DeenDaily.saveFile(s.split(',')[1] || '', a.download, b.type || 'application/octet-stream');
        };
        fr.readAsDataURL(b);
      }).catch(function () {});
      return;
    }
    return origClick.call(a);
  };

  /* ---- অ্যাপ বন্ধ থাকলেও নোটিফিকেশন: ১৪ দিনের সময়সূচি native অ্যালার্মে পাঠানো ---- */
  var lastSig = null;
  window.__ddScheduled = false;

  function hm(str) {
    var m = /^(\d{1,2}):(\d{2})$/.exec(String(str || ''));
    return m ? [+m[1], +m[2]] : null;
  }

  function buildJobs() {
    if (!window.DD || !DD.store || !DD.prayerTimes) return null;
    var jobs = [];
    var master = false;
    try { master = !!DD.store.get('notifMaster', false); } catch (e) {}
    if (!master) return jobs;
    var n = {}; try { n = DD.store.get('notif', {}) || {}; } catch (e) {}
    var customs = []; try { customs = DD.customAmols.list() || []; } catch (e) {}
    var fp = {}; try { fp = DD.fajr.get() || {}; } catch (e) {}
    var names = { fajr: 'ফজর', dhuhr: 'যোহর', asr: 'আসর', maghrib: 'মাগরিব', isha: 'এশা' };
    var now = Date.now();
    for (var day = 0; day < 14; day++) {
      var d = new Date(); d.setHours(12, 0, 0, 0); d.setDate(d.getDate() + day);
      var ds = DD.todayKey(d), fri = (d.getDay() === 5), t;
      try { t = DD.prayerTimes(d); } catch (e) { continue; }
      var add = function (id, time, title, body) {
        if (time && time.getTime() > now) jobs.push({ id: ds + '-' + id, t: time.getTime(), title: title, body: body });
      };
      var shift = function (base, min) { return base ? new Date(base.getTime() + min * 60000) : null; };
      if (n.ntSalah !== false) {
        ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'].forEach(function (k) {
          add('salah-' + k, t[k], 'নামাজের সময়', ((k === 'dhuhr' && fri) ? "জুম'আ" : names[k]) + ' ওয়াক্ত শুরু হয়েছে');
        });
      }
      if (n.ntQuran !== false) add('quran', shift(t.fajr, 30), 'কুরআন তিলাওয়াত', 'কুরআন পড়ার সময় হয়েছে');
      if (n.ntMorning !== false) add('morning', shift(t.sunrise, 30), 'সকালের জিকির', 'সকালের জিকিরের সময় হয়েছে');
      if (n.ntEvening !== false) add('evening', shift(t.maghrib, -30), 'সন্ধ্যার জিকির', 'সন্ধ্যার জিকিরের সময় হয়েছে');
      if (n.ntHadith !== false) add('hadith', shift(t.isha, 30), 'হাদিস', 'আজকের হাদিস পড়ার সময় হয়েছে');
      if (n.ntTahajjud !== false) add('tahajjud', shift(t.fajr, -90), 'তাহাজ্জুদ', 'তাহাজ্জুদের সময় হয়েছে — উঠে পড়ুন');

      var fh = hm(fp.reminder);
      if (fh) {
        var ft = new Date(d); ft.setHours(fh[0], fh[1], 0, 0);
        add('fajrplan', ft, 'ফজর জাগরণ', 'আপনার সেট করা রিমাইন্ডারের সময় হয়েছে');
      }
      for (var ci = 0; ci < customs.length; ci++) {
        var c = customs[ci];
        if (!c || c.active === false) continue;
        var ch = hm(c.reminder);
        if (!ch) continue;
        if (c.cadence === 'weekly') {
          var cd = new Date(+String(c.id || '').slice(1));
          if (isNaN(cd.getTime()) || cd.getDay() !== d.getDay()) continue;
        }
        var ct = new Date(d); ct.setHours(ch[0], ch[1], 0, 0);
        add('c-' + c.id, ct, c.name || 'আমল', 'আপনার আমলের রিমাইন্ডার');
      }
    }
    return jobs.slice(0, 300);
  }

  function sync() {
    try {
      var jobs = buildJobs();
      if (jobs === null) return;
      var json = JSON.stringify(jobs);
      if (json === lastSig) return;
      window.DeenDaily.scheduleNotifications(json);
      lastSig = json;
      window.__ddScheduled = true;
    } catch (e) {}
  }
  setTimeout(sync, 3000);
  setInterval(sync, 20000);
  document.addEventListener('visibilitychange', function () { if (!document.hidden) sync(); });
})();
