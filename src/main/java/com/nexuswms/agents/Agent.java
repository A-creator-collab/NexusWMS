package com.nexuswms.agents;

import java.util.List;

public interface Agent {
    String name();
    /** Key used by SettingsService.agentEnabled(). */
    String settingsKey();
    /** Pure(ish) decision step: reads the context, may call an LLM, never writes to the database. */
    List<ProposedAction> propose(AgentContext ctx);
    /** One-line description of how the last run decided (e.g. "AI (3 proposals)" or "fallback: AUTH ..."). */
    default String diagnostics() { return ""; }
    /** True when the last run had to fall back because the AI was configured but failed (worth a user warning). */
    default boolean degraded() { return false; }
}
