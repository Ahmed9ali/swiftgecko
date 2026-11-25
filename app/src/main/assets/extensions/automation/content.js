// Swift Automation content script. The app talks to it through a native port named "browser".
// Commands arrive as {id, cmd, ...}; every command answers {id, ok, ...}.
(() => {
  if (window.__swiftAutomation) return;
  window.__swiftAutomation = true;

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  function byText(text) {
    const xp = `//*[self::a or self::button or self::span or self::div or self::li or self::label or self::input]` +
               `[contains(normalize-space(.), ${JSON.stringify(text)}) or @value=${JSON.stringify(text)}]`;
    const r = document.evaluate(xp, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
    return r.snapshotLength ? r.snapshotItem(r.snapshotLength - 1) : null;
  }
  function find(m) {
    if (m.selector) return document.querySelector(m.selector);
    if (m.text) return byText(m.text);
    return null;
  }
  async function waitFor(m) {
    const end = Date.now() + (m.timeout || 10000);
    while (Date.now() < end) {
      const el = find(m);
      if (el) return el;
      await sleep(100);
    }
    return null;
  }
  function setValue(el, value) {
    const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, "value");
    if (setter && setter.set) setter.set.call(el, value); else el.value = value;
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  }

  const handlers = {
    async click(m) {
      const el = await waitFor(m);
      if (!el) return { ok: false, error: "element not found" };
      el.scrollIntoView({ block: "center" });
      el.click();
      return { ok: true };
    },
    async type(m) {
      const el = await waitFor(m);
      if (!el) return { ok: false, error: "element not found" };
      el.focus();
      setValue(el, m.clear === false ? (el.value || "") + m.value : m.value);
      if (m.enter) {
        for (const t of ["keydown", "keypress", "keyup"])
          el.dispatchEvent(new KeyboardEvent(t, { key: "Enter", code: "Enter", keyCode: 13, bubbles: true }));
        if (el.form) el.form.requestSubmit ? el.form.requestSubmit() : el.form.submit();
      }
      return { ok: true };
    },
    async waitFor(m) {
      const el = await waitFor(m);
      return { ok: !!el, error: el ? undefined : "timeout" };
    },
    async getText(m) {
      const el = m.selector || m.text ? await waitFor(m) : document.body;
      return el ? { ok: true, value: (el.innerText || el.textContent || "").trim() } : { ok: false, error: "element not found" };
    },
    async getHtml(m) {
      const el = m.selector ? await waitFor(m) : document.documentElement;
      return el ? { ok: true, value: el.outerHTML } : { ok: false, error: "element not found" };
    },
    async getAttr(m) {
      const el = await waitFor(m);
      return el ? { ok: true, value: el.getAttribute(m.attr) } : { ok: false, error: "element not found" };
    },
    async scroll(m) {
      window.scrollTo({ top: m.to === "bottom" ? document.body.scrollHeight : (m.y || 0), behavior: "instant" });
      return { ok: true };
    },
    async eval(m) {
      try { const v = (0, eval)(m.code); return { ok: true, value: JSON.stringify(await v) }; }
      catch (e) { return { ok: false, error: String(e) }; }
    },
    async info() {
      return { ok: true, value: JSON.stringify({ url: location.href, title: document.title, ready: document.readyState }) };
    },
  };

  let port;
  try { port = browser.runtime.connectNative("browser"); } catch (e) { return; }
  port.onMessage.addListener(async (m) => {
    const h = handlers[m.cmd];
    let res;
    try { res = h ? await h(m) : { ok: false, error: "unknown cmd " + m.cmd }; }
    catch (e) { res = { ok: false, error: String(e) }; }
    port.postMessage(Object.assign({ id: m.id }, res));
  });
  port.postMessage({ id: -1, ok: true, hello: location.href });
})();
