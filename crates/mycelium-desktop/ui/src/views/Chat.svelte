<script>
  import { invoke } from "@tauri-apps/api/core";
  import { listen } from "@tauri-apps/api/event";
  let { peerId } = $props();
  let selectedPeer = $state("");
  let peers = $state([]);
  let messages = $state([]);
  let input = $state("");
  let isEncrypted = $state(false);
  let errorMsg = $state("");

  async function refresh() {
    peers = await invoke("get_peers");
    if (selectedPeer) {
      messages = await invoke("chat_history", { peerId: selectedPeer, limit: 100 });
      isEncrypted = await invoke("peer_has_enc_key", { peerId: selectedPeer });
    } else {
      isEncrypted = false;
    }
  }

  async function send() {
    if (!input.trim() || !selectedPeer) return;
    errorMsg = "";
    try {
      await invoke("send_chat_encrypted", { toPeer: selectedPeer, body: input });
      input = "";
      await refresh();
    } catch (e) {
      const msg = String(e);
      if (msg.includes("enc_key_not_yet_exchanged")) {
        errorMsg =
          "Key exchange pending — message not sent. Try again shortly.";
      } else {
        errorMsg = msg;
      }
    }
  }

  $effect(() => {
    refresh();
    const unsubs = [];
    (async () => {
      unsubs.push(await listen("chat-updated", refresh));
      unsubs.push(await listen("metrics-updated", refresh));
    })();
    return () => unsubs.forEach((u) => u?.());
  });
</script>

<div class="chat-layout">
  <div class="sidebar">
    {#each peers as peer}
      <button onclick={() => ((selectedPeer = peer), refresh())} class:active={selectedPeer === peer}>
        {peer.slice(0, 16)}...
      </button>
    {/each}
  </div>
  <div class="conversation">
    <div class="enc-badge" class:encrypted={isEncrypted}>
      {isEncrypted
        ? "🔒 End-to-end encrypted"
        : "⚠ Not encrypted – peer key not yet exchanged"}
    </div>
    {#if errorMsg}
      <div class="error-banner">{errorMsg}</div>
    {/if}
    <div class="messages">
      {#each messages as msg}
        <div class="msg" class:own={msg.from_peer === peerId}>
          <span class="name">{msg.from_display_name}</span>
          <span class="body">{msg.body}</span>
        </div>
      {/each}
    </div>
    <div class="input-row">
      <input bind:value={input} onkeydown={(e) => e.key === "Enter" && send()} placeholder="Message..." disabled={!selectedPeer} />
      <button onclick={send} disabled={!selectedPeer}>Send</button>
    </div>
  </div>
</div>

<style>
  .chat-layout { display: flex; height: 100%; }
  .sidebar { width: 220px; border-right: 1px solid var(--border); overflow-y: auto; padding: 8px; }
  .sidebar button { display: block; width: 100%; text-align: left; padding: 8px; border-radius: 6px; border: none; background: none; cursor: pointer; font-size: 13px; font-family: monospace; }
  .sidebar button.active { background: var(--accent-subtle); }
  .conversation { flex: 1; display: flex; flex-direction: column; }
  .enc-badge {
    font-size: 12px;
    padding: 6px 10px;
    border-bottom: 1px solid var(--border);
    background: var(--surface-variant, rgba(128, 128, 128, 0.12));
    color: var(--text-muted);
  }
  .enc-badge.encrypted {
    background: var(--accent-subtle);
    color: var(--text);
  }
  .error-banner {
    font-size: 12px;
    padding: 8px 10px;
    background: rgba(200, 60, 60, 0.15);
    color: #c0392b;
    border-bottom: 1px solid var(--border);
  }
  .messages { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 8px; }
  .msg { max-width: 70%; padding: 8px 12px; border-radius: 10px; background: var(--border); }
  .msg.own { align-self: flex-end; background: var(--accent-subtle); }
  .name { font-size: 11px; color: var(--text-muted); display: block; margin-bottom: 2px; }
  .input-row { display: flex; gap: 8px; padding: 12px; border-top: 1px solid var(--border); }
  input { flex: 1; padding: 8px 12px; border-radius: 6px; border: 1px solid var(--border); background: none; color: inherit; font-size: 14px; }
  button { padding: 8px 16px; background: var(--accent); color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; }
</style>
