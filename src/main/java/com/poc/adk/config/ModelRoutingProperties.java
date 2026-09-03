package com.poc.adk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public class ModelRoutingProperties {

  public enum LlmProvider {
    ollama,
    gemini,
    anthropic,
    openrouter
  }

  private LlmProvider provider = LlmProvider.ollama;
  private Ollama ollama = new Ollama();
  private Gemini gemini = new Gemini();
  private Anthropic anthropic = new Anthropic();
  private OpenRouter openrouter = new OpenRouter();

  public LlmProvider getProvider() {
    return provider;
  }

  public void setProvider(LlmProvider provider) {
    this.provider = provider;
  }

  public Ollama getOllama() {
    return ollama;
  }

  public void setOllama(Ollama ollama) {
    this.ollama = ollama;
  }

  public Gemini getGemini() {
    return gemini;
  }

  public void setGemini(Gemini gemini) {
    this.gemini = gemini;
  }

  public Anthropic getAnthropic() {
    return anthropic;
  }

  public void setAnthropic(Anthropic anthropic) {
    this.anthropic = anthropic;
  }

  public OpenRouter getOpenrouter() {
    return openrouter;
  }

  public void setOpenrouter(OpenRouter openrouter) {
    this.openrouter = openrouter;
  }

  public static class Ollama {
    private String baseUrl = "http://localhost:11434/v1/";
    private String model = "qwen2.5:7b";

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }
  }

  public static class Gemini {
    private String apiKey = "";
    private String model = "gemini-2.0-flash";

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }
  }

  public static class Anthropic {
    private String apiKey = "";
    private String model = "claude-3-7-sonnet-20250219";

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }
  }

  public static class OpenRouter {
    private String apiKey = "";
    private String baseUrl = "https://openrouter.ai/api/v1/";
    private String model = "openrouter/auto";

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }
  }
}
