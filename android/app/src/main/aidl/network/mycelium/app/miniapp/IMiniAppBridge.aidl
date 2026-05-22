package network.mycelium.app.miniapp;

/** Cross-process bridge to the main Mycelium node (H16). */
interface IMiniAppBridge {
    String bridgeCall(String appId, String method, String argsJson);
    String issueSession(String appId);
    String issueCapability(String appId, String permission, String sessionToken);
    void revokeSession(String appId);
    boolean hasGrant(String appId, String permission);
    void setGrant(String appId, String permission, boolean granted);
    String getPolicyJson(String appId);
    String getAppDisplayName(String appId);
    byte[] getAppFile(String appId, String path);
    String getAppEntry(String appId);
    String getAppPaymentAddress(String appId);
    long getWalletConfirmedMuon();
    void sendTransaction(String toAddress, long amountMuon, String memo);
}
