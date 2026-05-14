<script>
  import { invoke } from "@tauri-apps/api/core";

  let { appId } = $props();

  let iframe;
  let html = $state("");

  async function load() {
    html = await invoke("miniapp_get_entry_html", { appId });
  }

  function onMessage(ev) {
    const raw = ev.data && ev.data.__mycelium_call;
    if (raw == null) return;
    let payload;
    try {
      payload = typeof raw === "string" ? JSON.parse(raw) : raw;
    } catch {
      return;
    }
    const id = payload.id;
    const method = payload.method;
    const args = payload.args ?? {};
    invoke("miniapp_bridge_call", { appId, method, args })
      .then((result) => {
        iframe?.contentWindow?.postMessage(
          { __mycelium_resolve: { id, result, error: null } },
          "*"
        );
      })
      .catch((e) => {
        const msg = e?.toString?.() ? String(e) : "error";
        iframe?.contentWindow?.postMessage(
          { __mycelium_resolve: { id, result: null, error: msg } },
          "*"
        );
      });
  }

  $effect(() => {
    load();
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  });
</script>

<div class="wrap">
  <iframe
    bind:this={iframe}
    srcdoc={html}
    title="Mini app"
    sandbox="allow-scripts allow-forms allow-popups"
    class="frame"
  ></iframe>
</div>

<style>
  .wrap {
    height: 100%;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .frame {
    flex: 1;
    width: 100%;
    border: none;
    background: #fff;
  }
</style>
