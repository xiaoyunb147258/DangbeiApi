/* Hallmark · component: app-shell · genre: modern-minimal · theme: Cobalt
 * states: default · hover · focus · active · disabled
 * contrast: pass (46–50)
 */
(function () {
  "use strict";

  var Native = window.DangbeiNative || null;
  var hasNative = !!Native;

  function call(fn) {
    if (!Native || typeof Native[fn] !== "function") return null;
    try { return Native[fn].apply(Native, Array.prototype.slice.call(arguments, 1)); }
    catch (e) { return null; }
  }

  /* ── toast：仅失败/异步可见事件 ── */
  var toastBox = document.getElementById("toasts");
  function toast(msg, isErr) {
    var el = document.createElement("div");
    el.className = "toast" + (isErr ? " is-err" : "");
    el.textContent = msg;
    toastBox.appendChild(el);
    setTimeout(function () { el.remove(); }, 2600);
  }

  /* ── tab 切换 ── */
  var tabs = document.querySelectorAll(".tab");
  var views = document.querySelectorAll(".view");
  function showView(name) {
    tabs.forEach(function (t) {
      var on = t.getAttribute("data-view") === name;
      t.classList.toggle("is-active", on);
      t.setAttribute("aria-selected", on ? "true" : "false");
    });
    views.forEach(function (v) {
      v.classList.toggle("is-active", v.id === "view-" + name);
    });
    if (name === "settings") refreshLogs();
  }
  tabs.forEach(function (t) {
    t.addEventListener("click", function () { showView(t.getAttribute("data-view")); });
  });

  /* ── 状态刷新 ── */
  var el = {
    dot: document.getElementById("statusDot"),
    state: document.getElementById("statusState"),
    meta: document.getElementById("statusMeta"),
    chip: document.getElementById("tokenChip"),
    runBadge: document.getElementById("runBadge"),
    loginBadge: document.getElementById("loginBadge"),
    tokenHint: document.getElementById("tokenHint"),
    baseUrl: document.getElementById("baseUrl"),
    apiKeyShow: document.getElementById("apiKeyShow"),
    navToggle: document.getElementById("navToggle"),
    runBtn: document.getElementById("runBtn")
  };

  var lastRunning = null;

  function refreshStatus() {
    var raw = call("getStatus");
    if (!raw) return;
    var s;
    try { s = JSON.parse(raw); } catch (e) { return; }

    el.state.textContent = s.running ? "运行中" : "未运行";
    el.meta.textContent = "端口 " + s.port + " · 已处理 " + s.requestCount + " 次请求";
    el.dot.classList.toggle("is-on", !!s.running);
    el.runBadge.textContent = s.running ? "RUNNING" : "IDLE";
    el.runBadge.classList.toggle("is-on", !!s.running);

    if (s.hasToken) {
      el.chip.textContent = s.tokenPreview || "已登录";
      el.chip.classList.add("is-ok");
      el.loginBadge.textContent = "SAVED";
      el.loginBadge.classList.add("is-on");
      el.tokenHint.textContent = "已保存 token（" + (s.tokenPreview || "") + "）。失效就重新登录一次。";
    } else {
      el.chip.textContent = "未登录";
      el.chip.classList.remove("is-ok");
      el.loginBadge.textContent = "—";
      el.loginBadge.classList.remove("is-on");
      el.tokenHint.textContent = "还没有保存 token。点下面按钮打开登录页，在页面里登录当贝 AI，然后点右上角「保存」。";
    }

    el.baseUrl.textContent = s.baseUrl || ("http://" + s.ip + ":" + s.port + "/v1");
    el.navToggle.textContent = s.running ? "停止网关" : "启动网关";

    if (lastRunning !== s.running) {
      lastRunning = s.running;
    }
  }

  /* ── 日志 ── */
  var logBody = document.getElementById("logBody");
  function refreshLogs() {
    var raw = call("getLogs");
    if (!raw) { logBody.textContent = "—"; return; }
    var arr;
    try { arr = JSON.parse(raw); } catch (e) { return; }
    logBody.textContent = arr.length ? arr.join("\n") : "—";
    logBody.scrollTop = logBody.scrollHeight;
  }

  /* ── 设置 ── */
  var setPort = document.getElementById("setPort");
  var setApiKey = document.getElementById("setApiKey");
  var setDefaultModel = document.getElementById("setDefaultModel");
  var setAutoStart = document.getElementById("setAutoStart");
  var setUseSearch = document.getElementById("setUseSearch");
  var setShowFloat = document.getElementById("setShowFloat");
  var setBattery = document.getElementById("setBattery");

  function refreshSettings() {
    var raw = call("getSettings");
    if (!raw) return;
    var s;
    try { s = JSON.parse(raw); } catch (e) { return; }
    setPort.value = s.port;
    setApiKey.value = s.apiKey || "";
    setDefaultModel.value = s.defaultModel || "glm-5";
    setAutoStart.checked = !!s.autoStart;
    setUseSearch.checked = !!s.useSearch;
    setShowFloat.checked = !!s.showFloat;
    // 电池优化状态从原生实时查
    try { setBattery.checked = !!call("isIgnoringBattery"); } catch (e) {}
  }

  // 悬浮窗开关：即时生效，并申请权限
  if (setShowFloat) {
    setShowFloat.addEventListener("change", function () {
      if (setShowFloat.checked) {
        var has = call("hasFloatPermission");
        if (!has) {
          call("requestOverlay");
          toast("请授予悬浮窗权限后返回", false);
        }
        call("showFloat");
        toast("悬浮窗已开启");
      } else {
        call("hideFloat");
        toast("悬浮窗已关闭");
      }
    });
  }

  // 保活开关：触发忽略电池优化授权
  if (setBattery) {
    setBattery.addEventListener("change", function () {
      if (setBattery.checked) {
        call("requestIgnoreBattery");
        toast("请在弹窗中允许忽略电池优化", false);
      } else {
        toast("请到系统设置手动恢复电池优化", false);
        setBattery.checked = true;
      }
    });
  }

  document.getElementById("saveSettings").addEventListener("click", function () {
    var payload = JSON.stringify({
      port: parseInt(setPort.value, 10) || 9980,
      apiKey: setApiKey.value.trim(),
      autoStart: setAutoStart.checked,
      useSearch: setUseSearch.checked,
      defaultModel: setDefaultModel.value,
      showFloat: setShowFloat ? setShowFloat.checked : false
    });
    call("saveSettings", payload);
    toast("设置已保存");
    refreshStatus();
  });

  /* ── 模型表 ── */
  function refreshModels() {
    var rows = document.getElementById("modelRows");
    var raw = call("getModels");
    if (!rows || !raw) return;
    var arr;
    try { arr = JSON.parse(raw); } catch (e) { return; }
    // 拉取每个模型当前的思考开关状态
    var thinkMap = {};
    try { thinkMap = JSON.parse(call("getModelThink") || "{}"); } catch (e) {}
    rows.innerHTML = "";
    arr.forEach(function (m) {
      var tr = document.createElement("tr");
      var td1 = document.createElement("td");
      td1.textContent = m.id;
      var td2 = document.createElement("td");
      if (m.think) {
        // 支持思考 → 渲染开关
        var label = document.createElement("label");
        label.className = "switch switch--sm";
        var cb = document.createElement("input");
        cb.type = "checkbox";
        cb.checked = !!thinkMap[m.id];
        cb.addEventListener("change", function () {
          call("setModelThink", m.id, cb.checked);
          toast((cb.checked ? "已开启" : "已关闭") + " " + (m.label || m.id) + " 深度思考");
        });
        var track = document.createElement("span");
        track.className = "switch__track";
        label.appendChild(cb);
        label.appendChild(track);
        td2.appendChild(label);
      } else {
        td2.className = "no";
        td2.textContent = "—";
      }
      var td3 = document.createElement("td");
      td3.className = "yes";
      td3.textContent = "支持";
      tr.appendChild(td1); tr.appendChild(td2); tr.appendChild(td3);
      rows.appendChild(tr);
    });
    // 默认模型下拉
    setDefaultModel.innerHTML = "";
    arr.forEach(function (m) {
      var op = document.createElement("option");
      op.value = m.id;
      op.textContent = m.label || m.id;
      setDefaultModel.appendChild(op);
    });
  }

  /* ── 按钮绑定 ── */
  function bind(id, fn) {
    var b = document.getElementById(id);
    if (b) b.addEventListener("click", fn);
  }

  bind("runBtn", function () { call("startGateway"); toast("正在启动网关"); setTimeout(refreshStatus, 700); });
  bind("restartBtn", function () { call("restartGateway"); toast("正在重启"); setTimeout(refreshStatus, 700); });
  bind("stopBtn", function () { call("stopGateway"); toast("网关已停止"); setTimeout(refreshStatus, 500); });
  bind("loginBtn", function () { call("openLogin"); });
  bind("clearTokenBtn", function () { call("clearToken"); toast("token 已清除"); setTimeout(refreshStatus, 300); });
  bind("clearLogs", function () { call("clearLogs"); refreshLogs(); });

  el.navToggle.addEventListener("click", function () {
    var raw = call("getStatus");
    var running = false;
    try { running = !!JSON.parse(raw).running; } catch (e) {}
    if (running) { call("stopGateway"); toast("网关已停止"); }
    else { call("startGateway"); toast("正在启动网关"); }
    setTimeout(refreshStatus, 700);
  });

  /* ── 复制 ── */
  document.querySelectorAll(".copy").forEach(function (btn) {
    btn.addEventListener("click", function () {
      var id = btn.getAttribute("data-copy");
      var target = document.getElementById(id);
      if (!target) return;
      var text = target.textContent;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function () { toast("已复制"); });
      } else {
        // 兜底
        var ta = document.createElement("textarea");
        ta.value = text; document.body.appendChild(ta);
        ta.select(); try { document.execCommand("copy"); toast("已复制"); } catch (e) { toast("复制失败", true); }
        ta.remove();
      }
    });
  });

  /* ── 登录保存回调（Android 调用） ── */
  window.onTokenSaved = function () {
    toast("token 已保存");
    refreshStatus();
  };

  /* ── reveal ── */
  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      if (e.isIntersecting) { e.target.classList.add("is-in"); io.unobserve(e.target); }
    });
  }, { threshold: 0.08 });
  document.querySelectorAll(".reveal").forEach(function (r) { io.observe(r); });

  /* ── 初始化 ── */
  function init() {
    if (!hasNative) {
      // 浏览器里预览：给一个静态占位
      el.state.textContent = "预览模式";
      el.meta.textContent = "在 App 内打开以连接原生";
      return;
    }
    refreshModels();
    refreshSettings();
    refreshStatus();
    refreshLogs();
    setInterval(refreshStatus, 2000);
    setInterval(refreshLogs, 3000);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
