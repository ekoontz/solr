package org.apache.solr.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.core.IndexDeletionPolicyWrapper;
import org.apache.solr.core.RequestHandlers;
import org.apache.solr.core.SolrEventListener;
import org.apache.solr.core.SolrInfoMBean;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.highlight.SolrHighlighter;
import org.apache.solr.request.QueryResponseWriter;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.ValueSourceParser;
import org.apache.solr.update.processor.UpdateRequestProcessorChain;

public class CoreVars {
  CoreInitalizer initalizer;
  SolrConfiguraion configuration;
  IndexSchema schema;
  
  RequestHandlers reqHandlers;
  SolrHighlighter highlighter;
  Map<String,SearchComponent> searchComponents;
  Map<String,UpdateRequestProcessorChain> updateProcessorChains;
  Map<String, SolrInfoMBean> infoRegistry;
  IndexDeletionPolicyWrapper solrDelPolicy;
  QueryResponseWriter defaultResponseWriter;

  Map<String, QueryResponseWriter> responseWriters = new HashMap<String, QueryResponseWriter>();
  Map<String, QParserPlugin> qParserPlugins = new HashMap<String, QParserPlugin>();
  HashMap<String, ValueSourceParser> valueSourceParsers = new HashMap<String, ValueSourceParser>();

  List<SolrEventListener> firstSearcherListeners;
  List<SolrEventListener> newSearcherListeners;

  List<SolrEventListener> commitCallbacks = new ArrayList<SolrEventListener>();
  List<SolrEventListener> optimizeCallbacks = new ArrayList<SolrEventListener>();

  
  public RequestHandlers getReqHandlers() {
    return reqHandlers;
  }
  public void setReqHandlers(RequestHandlers reqHandlers) {
    this.reqHandlers = reqHandlers;
  }
  public SolrHighlighter getHighlighter() {
    return highlighter;
  }
  public void setHighlighter(SolrHighlighter highlighter) {
    this.highlighter = highlighter;
  }
  public Map<String, SearchComponent> getSearchComponents() {
    return searchComponents;
  }
  public void setSearchComponents(Map<String, SearchComponent> searchComponents) {
    this.searchComponents = searchComponents;
  }
  public Map<String, UpdateRequestProcessorChain> getUpdateProcessorChains() {
    return updateProcessorChains;
  }
  public void setUpdateProcessorChains(
      Map<String, UpdateRequestProcessorChain> updateProcessorChains) {
    this.updateProcessorChains = updateProcessorChains;
  }
  public Map<String, SolrInfoMBean> getInfoRegistry() {
    return infoRegistry;
  }
  public void setInfoRegistry(Map<String, SolrInfoMBean> infoRegistry) {
    this.infoRegistry = infoRegistry;
  }
  public IndexDeletionPolicyWrapper getSolrDelPolicy() {
    return solrDelPolicy;
  }
  public void setSolrDelPolicy(IndexDeletionPolicyWrapper solrDelPolicy) {
    this.solrDelPolicy = solrDelPolicy;
  }
  public QueryResponseWriter getDefaultResponseWriter() {
    return defaultResponseWriter;
  }
  public void setDefaultResponseWriter(QueryResponseWriter defaultResponseWriter) {
    this.defaultResponseWriter = defaultResponseWriter;
  }
  public CoreInitalizer getInitalizer() {
    return initalizer;
  }
  public void setInitalizer(CoreInitalizer initalizer) {
    this.initalizer = initalizer;
  }
  public SolrConfiguraion getConfiguration() {
    return configuration;
  }
  public void setConfiguration(SolrConfiguraion configuration) {
    this.configuration = configuration;
  }
  public IndexSchema getSchema() {
    return schema;
  }
  public void setSchema(IndexSchema schema) {
    this.schema = schema;
  }
  public Map<String, QueryResponseWriter> getResponseWriters() {
    return responseWriters;
  }
  public void setResponseWriters(Map<String, QueryResponseWriter> responseWriters) {
    this.responseWriters = responseWriters;
  }
  public Map<String, QParserPlugin> getQParserPlugins() {
    return qParserPlugins;
  }
  public void setQParserPlugins(Map<String, QParserPlugin> parserPlugins) {
    qParserPlugins = parserPlugins;
  }
  public HashMap<String, ValueSourceParser> getValueSourceParsers() {
    return valueSourceParsers;
  }
  public void setValueSourceParsers(
      HashMap<String, ValueSourceParser> valueSourceParsers) {
    this.valueSourceParsers = valueSourceParsers;
  }
  public List<SolrEventListener> getFirstSearcherListeners() {
    return firstSearcherListeners;
  }
  public void setFirstSearcherListeners(
      List<SolrEventListener> firstSearcherListeners) {
    this.firstSearcherListeners = firstSearcherListeners;
  }
  public List<SolrEventListener> getNewSearcherListeners() {
    return newSearcherListeners;
  }
  public void setNewSearcherListeners(List<SolrEventListener> newSearcherListeners) {
    this.newSearcherListeners = newSearcherListeners;
  }
  public List<SolrEventListener> getCommitCallbacks() {
    return commitCallbacks;
  }
  public void setCommitCallbacks(List<SolrEventListener> commitCallbacks) {
    this.commitCallbacks = commitCallbacks;
  }
  public List<SolrEventListener> getOptimizeCallbacks() {
    return optimizeCallbacks;
  }
  public void setOptimizeCallbacks(List<SolrEventListener> optimizeCallbacks) {
    this.optimizeCallbacks = optimizeCallbacks;
  }
}
