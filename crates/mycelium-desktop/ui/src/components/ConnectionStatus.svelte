<script>
  let { peerCount = 0 } = $props();

  let showConnected = $state(false);
  let prevPeers = $state(0);

  $effect(() => {
    if (peerCount > 0 && prevPeers === 0) {
      showConnected = true;
      const t = setTimeout(() => {
        showConnected = false;
      }, 4000);
      prevPeers = peerCount;
      return () => clearTimeout(t);
    }
    if (peerCount === 0) {
      showConnected = false;
      prevPeers = 0;
    } else {
      prevPeers = peerCount;
    }
  });
</script>

{#if peerCount === 0}
  <div class="status-banner searching">
    <span class="spinner"></span>
    Searching for peers via relay…
  </div>
{:else if showConnected}
  <div class="status-banner connected">
    <span class="dot"></span>
    {peerCount} peer{peerCount !== 1 ? "s" : ""} connected
  </div>
{/if}

<style>
  .status-banner {
    padding: 8px 16px;
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
    border-bottom: 1px solid var(--border);
  }
  .searching {
    background: var(--sidebar-bg);
    color: var(--text-muted);
  }
  .connected {
    background: var(--accent-subtle);
    color: var(--text-secondary);
  }
  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--accent);
    flex-shrink: 0;
  }
  .spinner {
    width: 14px;
    height: 14px;
    border: 2px solid var(--muted);
    border-top-color: var(--text-muted);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }
  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
