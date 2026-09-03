package com.poc.adk.eval;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * Layer 3 entry point: Playbook §1–8 agent graphs via {@code InMemoryRunner}.
 *
 * <p>Members use {@link ScriptedLlm} (deterministic; no chat LLM, no LLM-as-judge). Run with {@code
 * mvn test -Dtest=EvaluationHarnessTest}. Per-agent classes remain independently runnable.
 */
@Suite
@SelectPackages("com.poc.adk.agents")
@IncludeClassNamePatterns(".*EvalTest")
class EvaluationHarnessTest {}
