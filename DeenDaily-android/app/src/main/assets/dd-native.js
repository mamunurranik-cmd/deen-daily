(function () {
  if (!window.DeenDaily || window.__ddNative) return;
  window.__ddNative = true;

  /* ---- Notification: Android WebView-তে নেই, তাই native দিয়ে ---- */
  var waiting = [];
  function Notif(title, opts) {
    opts = opts || {};
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
})();
