package org.apache.solr.config;

public class JmxConfiguration 
{
  public final boolean enabled;
  public final String agentId;
  public final String serviceUrl;

  public JmxConfiguration(boolean enabled, String agentId, String serviceUrl) {
    this.enabled = enabled;
    this.agentId = agentId;
    this.serviceUrl = serviceUrl;
  }
}