<script>
  // SPDX-License-Identifier: AGPL-3.0-or-later
  // Copyright (C) 2026 Mycelium Project
  import { invoke } from '@tauri-apps/api/core';
  import { listen } from '@tauri-apps/api/event';
  import { onMount } from 'svelte';

  let listenSos = $state(false);
  let geohash = $state('');
  let message = $state('');
  let posts = $state([]);
  let saved = $state(false);
  let posting = $state(false);

  async function refresh() {
    posts = await invoke('list_sos_posts');
    const settings = await invoke('get_settings');
    listenSos = settings.listen_sos ?? false;
  }

  async function saveListen() {
    await invoke('set_listen_sos', { enabled: listenSos });
    saved = true;
    setTimeout(() => (saved = false), 1500);
  }

  async function postSos() {
    if (!geohash.trim() || !message.trim()) return;
    posting = true;
    try {
      await invoke('post_sos', {
        geohash: geohash.trim(),
        message: message.trim(),
        ttlSecs: 3600,
      });
      message = '';
      await refresh();
    } finally {
      posting = false;
    }
  }

  function openMeshAid() {
    window.dispatchEvent(
      new CustomEvent('mycelium-open-miniapp', {
        detail: { appId: 'network.mycelium.meshaid' },
      }),
    );
  }

  onMount(() => {
    refresh();
    const unsubs = [];
    listen('sos-updated', refresh).then((fn) => unsubs.push(fn));
    return () => unsubs.forEach((fn) => fn());
  });
</script>

<div class="sos">
  <header>
    <h3>Emergency SOS</h3>
    <p class="disclaimer">
      Broadcast a short help request to nearby mesh peers. <strong>Not a replacement for emergency services.</strong>
    </p>
  </header>

  <section>
    <h4>Listen for nearby SOS</h4>
    <label class="checkbox-row">
      <input type="checkbox" bind:checked={listenSos} onchange={saveListen} />
      Opt in to receive SOS posts (default off)
    </label>
    {#if saved}
      <p class="hint ok">Listening preference saved.</p>
    {/if}
  </section>

  <section>
    <h4>Post SOS</h4>
    <label for="sos-geohash">Geohash (approximate area, 4–12 chars)</label>
    <input id="sos-geohash" bind:value={geohash} placeholder="u4pru" maxlength="12" />
    <label for="sos-message">Short message</label>
    <textarea
      id="sos-message"
      bind:value={message}
      placeholder="Need help nearby — describe briefly"
      maxlength="280"
      rows="3"
    ></textarea>
    <button type="button" class="primary" disabled={posting} onclick={postSos}>
      {posting ? 'Posting…' : 'Post SOS (1 hour)'}
    </button>
  </section>

  <section>
    <h4>Nearby SOS ({posts.length})</h4>
    {#if !listenSos}
      <p class="hint">Enable listening above to receive posts from the mesh.</p>
    {:else if posts.length === 0}
      <p class="hint">No SOS posts stored yet.</p>
    {:else}
      <ul class="posts">
        {#each posts as p}
          <li>
            <strong>{p.geohash}</strong>
            <span>{p.message}</span>
          </li>
        {/each}
      </ul>
    {/if}
  </section>

  <section class="meshaid">
    <h4>Coordination threads</h4>
    <p class="hint">
      MeshAid remains available for structured help requests (medical, resolved threads, etc.).
    </p>
    <button type="button" class="secondary" onclick={openMeshAid}>Open MeshAid mini-app</button>
  </section>
</div>

<style>
  .sos {
    padding: 16px;
    max-width: 640px;
    display: flex;
    flex-direction: column;
    gap: 16px;
    color: var(--text);
  }
  header h3 {
    margin: 0 0 8px;
    font-size: 18px;
  }
  .disclaimer {
    margin: 0;
    font-size: 13px;
    color: var(--text-secondary);
  }
  section {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 16px;
    border: 1px solid var(--border);
    border-radius: 8px;
  }
  h4 {
    margin: 0;
    font-size: 13px;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-secondary);
  }
  label {
    font-size: 13px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .checkbox-row {
    flex-direction: row;
    align-items: center;
    gap: 8px;
  }
  input,
  textarea {
    padding: 8px 10px;
    border-radius: 6px;
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    font: inherit;
  }
  .hint {
    margin: 0;
    font-size: 12px;
    color: var(--text-muted);
  }
  .hint.ok {
    color: var(--accent);
  }
  button {
    align-self: flex-start;
    padding: 8px 14px;
    border-radius: 6px;
    border: none;
    cursor: pointer;
    font-size: 13px;
  }
  button.primary {
    background: #dc2626;
    color: white;
  }
  button.secondary {
    background: var(--surface);
    border: 1px solid var(--border);
    color: var(--text);
  }
  button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  .posts {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .posts li {
    padding: 10px;
    border-radius: 6px;
    background: var(--surface);
    border: 1px solid var(--border);
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .posts strong {
    font-family: ui-monospace, monospace;
    font-size: 12px;
  }
  .meshaid {
    border-color: var(--border);
  }
</style>
