<script>
  import { createEventDispatcher } from "svelte";
  import { invoke } from "@tauri-apps/api/core";

  const dispatch = createEventDispatcher();

  let step = $state(0);
  let displayName = $state("");

  async function finish() {
    if (!displayName.trim()) return;
    const dbPath = await invoke("get_default_db_path");
    dispatch("start", {
      dbPath,
      displayName: displayName.trim(),
      bootstrapPeers: [],
    });
  }
</script>

{#if step === 0}
  <div class="onboarding">
    <div class="icon">🍄</div>
    <h1>Mycelium</h1>
    <p>Communicate without internet. No servers. No accounts.</p>
    <button onclick={() => (step = 1)}>Get started →</button>
  </div>
{:else if step === 1}
  <div class="onboarding">
    <div class="features">
      <div class="feature">
        <span>🔒</span>
        <div>
          <strong>No servers</strong>
          <p>Messages travel directly between devices.</p>
        </div>
      </div>
      <div class="feature">
        <span>👤</span>
        <div>
          <strong>No account</strong>
          <p>No sign-up. Works immediately.</p>
        </div>
      </div>
      <div class="feature">
        <span>🗑️</span>
        <div>
          <strong>Panic wipe</strong>
          <p>Delete everything instantly from Settings.</p>
        </div>
      </div>
    </div>
    <button onclick={() => (step = 2)}>Continue →</button>
  </div>
{:else}
  <div class="onboarding">
    <h2>What should others call you?</h2>
    <p class="muted">You can use a pseudonym. Visible to people you message.</p>
    <input
      bind:value={displayName}
      placeholder="Your name or pseudonym"
      onkeydown={(e) => e.key === "Enter" && finish()}
    />
    <button onclick={finish} disabled={!displayName.trim()}>Start Mycelium</button>
  </div>
{/if}

<style>
  .onboarding {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100vh;
    gap: 20px;
    padding: 40px;
    max-width: 480px;
    margin: 0 auto;
  }
  .icon {
    font-size: 64px;
  }
  h1 {
    font-size: 28px;
    font-weight: 600;
    margin: 0;
  }
  h2 {
    font-size: 22px;
    font-weight: 600;
    margin: 0;
    text-align: center;
  }
  p {
    text-align: center;
    color: var(--text-muted);
    margin: 0;
  }
  .muted {
    font-size: 13px;
  }
  .features {
    display: flex;
    flex-direction: column;
    gap: 20px;
    width: 100%;
  }
  .feature {
    display: flex;
    gap: 16px;
    align-items: flex-start;
  }
  .feature span {
    font-size: 28px;
    flex-shrink: 0;
  }
  .feature strong {
    display: block;
    margin-bottom: 2px;
  }
  .feature p {
    text-align: left;
    font-size: 13px;
  }
  input {
    width: 100%;
    padding: 12px 16px;
    border-radius: 8px;
    border: 1px solid var(--border);
    background: none;
    color: inherit;
    font-size: 16px;
  }
  button {
    width: 100%;
    padding: 12px;
    background: var(--accent);
    color: white;
    border: none;
    border-radius: 8px;
    font-size: 15px;
    cursor: pointer;
  }
  button:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
</style>
