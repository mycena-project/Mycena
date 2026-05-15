<script>
  import { invoke } from "@tauri-apps/api/core";
  import { listen } from "@tauri-apps/api/event";

  let { onOpenApp = () => {} } = $props();

  let installed = $state([]);
  let listings = $state([]);
  let message = $state("");

  async function refresh() {
    try {
      installed = await invoke("miniapp_list_installed");
      listings = await invoke("miniapp_browse_store");
    } catch (e) {
      message = String(e);
    }
  }

  async function installFromFile(ev) {
    const file = ev.target?.files?.[0];
    if (!file) return;
    const buf = await file.arrayBuffer();
    const bytes = new Uint8Array(buf);
    let binary = "";
    for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
    const bundleBase64 = btoa(binary);
    try {
      await invoke("miniapp_install", { bundleBase64 });
      message = "Installed";
      await refresh();
    } catch (e) {
      message = String(e);
    }
    ev.target.value = "";
  }

  async function uninstall(appId) {
    try {
      await invoke("miniapp_uninstall", { appId });
      await refresh();
    } catch (e) {
      message = String(e);
    }
  }

  $effect(() => {
    refresh();
    const unsubs = [];
    (async () => {
      unsubs.push(await listen("appstore-updated", refresh));
    })();
    return () => unsubs.forEach((u) => u?.());
  });
</script>

<div class="store">
  <h3>Mini apps</h3>
  <label class="file">
    Install bundle (.mxa / .zip)
    <input type="file" accept=".zip,.mxa,application/zip" onchange={installFromFile} />
  </label>
  {#if message}
    <p class="msg">{message}</p>
  {/if}
  <section>
    <h4>Installed</h4>
    {#if installed.length === 0}
      <p class="muted">None yet.</p>
    {:else}
      <ul>
        {#each installed as app (app.id)}
          <li>
            <span class="title">{app.name}</span>
            <span class="meta">{app.id} · v{app.version}</span>
            <div class="row">
              <button type="button" onclick={() => onOpenApp(app.id)}>Open</button>
              <button type="button" class="danger" onclick={() => uninstall(app.id)}>Remove</button>
            </div>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
  <section>
    <h4>Catalog (cached listings)</h4>
    {#if listings.length === 0}
      <p class="muted">No listings on this node.</p>
    {:else}
      <ul>
        {#each listings as row (row.manifest.id + row.bundle_hash)}
          <li>
            <span class="title">{row.manifest.name}</span>
            <span class="meta">{row.manifest.id}</span>
            <span class="meta hash">{row.bundle_hash.slice(0, 16)}…</span>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
</div>

<style>
  .store {
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    height: 100%;
    overflow: auto;
  }
  h3,
  h4 {
    margin: 0;
  }
  .file {
    display: inline-block;
    padding: 8px 12px;
    border-radius: 8px;
    border: 1px solid var(--border);
    cursor: pointer;
    font-size: 14px;
  }
  .file input {
    display: none;
  }
  .msg {
    color: #b45309;
    font-size: 14px;
  }
  section {
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 12px;
  }
  ul {
    list-style: none;
    padding: 0;
    margin: 8px 0 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  li {
    border-bottom: 1px solid var(--border);
    padding-bottom: 10px;
  }
  li:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }
  .title {
    font-weight: 600;
    display: block;
  }
  .meta {
    font-size: 12px;
    color: var(--text-muted);
    display: block;
  }
  .hash {
    font-family: ui-monospace, monospace;
  }
  .row {
    display: flex;
    gap: 8px;
    margin-top: 8px;
  }
  button {
    padding: 6px 12px;
    border: none;
    border-radius: 6px;
    background: var(--accent);
    color: #fff;
    cursor: pointer;
    font-size: 13px;
  }
  button.danger {
    background: #c45c5c;
  }
  .muted {
    color: var(--text-muted);
    font-size: 14px;
  }
</style>
