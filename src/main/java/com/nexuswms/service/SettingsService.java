package com.nexuswms.service;

import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.prefs.Preferences;

/** Agent toggles, thresholds and approval policy (stored in java.util.prefs; changes are validated and audited). */
@Service
public class SettingsService {
    public static final String POLICY_ALL = "ALL", POLICY_OVER = "OVER_THRESHOLD", POLICY_NONFIN = "NON_FINANCIAL_NONE";
    private static final Set<String> POLICIES = Set.of(POLICY_ALL, POLICY_OVER, POLICY_NONFIN);
    private final Preferences prefs = Preferences.userRoot().node("nexuswms/settings");
    private final AuditService audit;

    public SettingsService(AuditService audit) { this.audit = audit; }

    public boolean agentEnabled(String key) { return prefs.getBoolean("agent." + key, true); }
    public double riskThreshold() { return clamp(prefs.getDouble("risk.threshold", 0.30), 0.01, 0.99); }
    public double poThreshold() { return Math.max(0, prefs.getDouble("po.threshold", 1000.0)); }
    public String approvalPolicy() { String p = prefs.get("approval.policy", POLICY_ALL); return POLICIES.contains(p) ? p : POLICY_ALL; }

    public void update(boolean replenishment, boolean risk, boolean order, double riskThreshold, double poThreshold, String policy, Long actor) {
        if (Double.isNaN(riskThreshold) || riskThreshold < 0.01 || riskThreshold > 0.99) throw new BusinessException("Risk threshold must be between 1% and 99%.");
        if (Double.isNaN(poThreshold) || poThreshold < 0 || poThreshold > 10_000_000) throw new BusinessException("Approval threshold must be between 0 and 10,000,000.");
        if (policy == null || !POLICIES.contains(policy)) throw new BusinessException("Unknown approval policy.");
        prefs.putBoolean("agent.replenishment", replenishment);
        prefs.putBoolean("agent.risk", risk);
        prefs.putBoolean("agent.order", order);
        prefs.putDouble("risk.threshold", riskThreshold);
        prefs.putDouble("po.threshold", poThreshold);
        prefs.put("approval.policy", policy);
        audit.log("SETTINGS", null, "UPDATE", actor, "replenishment=" + replenishment + " risk=" + risk + " order=" + order
                + " riskThreshold=" + riskThreshold + " poThreshold=" + poThreshold + " policy=" + policy);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
