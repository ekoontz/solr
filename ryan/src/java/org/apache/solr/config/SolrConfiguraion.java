/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.config;

import org.apache.solr.search.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This should not care about XML!
 * 
 * @version $Id: SolrConfig.java 701539 2008-10-03 21:26:50Z ryan $
 */
public class SolrConfiguraion  {
  public static Logger log = LoggerFactory.getLogger(SolrConfiguraion.class);
  
  String dataDir;
  int maxWarmingSearchers = Integer.MAX_VALUE;
  boolean useColdSearcher = false;
  boolean unlockOnStartup = false;
  
  /* The set of materialized parameters: */
  int booleanQueryMaxClauseCount;
  // SolrIndexSearcher - nutch optimizer
  boolean filtOptEnabled;
  int filtOptCacheSize;
  float filtOptThreshold;
  // SolrIndexSearcher - caches configurations
  CacheConfig filterCacheConfig ;
  CacheConfig queryResultCacheConfig;
  CacheConfig documentCacheConfig;
  CacheConfig[] userCacheConfigs;
  // SolrIndexSearcher - more...
  boolean useFilterForSortedQuery;
  int queryResultWindowSize;
  int queryResultMaxDocsCached;
  boolean enableLazyFieldLoading;
  // DocSet
  float hashSetInverseLoadFactor;
  int hashDocSetMaxSize;
  // default & main index configurations
  SolrIndexConfig defaultIndexConfig;
  SolrIndexConfig mainIndexConfig;
  
  //JMX configuration
  JmxConfiguration jmxConfig;
  
  //HTTP config
  HttpCachingConfiguration httpCachingConfig;
  RequestDispatcherConfiguration dispatcherConfig;

  //---------------------------------------------------------------------------------------
  //---------------------------------------------------------------------------------------

  public int getBooleanQueryMaxClauseCount() {
    return booleanQueryMaxClauseCount;
  }

  public void setBooleanQueryMaxClauseCount(int booleanQueryMaxClauseCount) {
    this.booleanQueryMaxClauseCount = booleanQueryMaxClauseCount;
  }

  public boolean isFiltOptEnabled() {
    return filtOptEnabled;
  }

  public void setFiltOptEnabled(boolean filtOptEnabled) {
    this.filtOptEnabled = filtOptEnabled;
  }

  public int getFiltOptCacheSize() {
    return filtOptCacheSize;
  }

  public void setFiltOptCacheSize(int filtOptCacheSize) {
    this.filtOptCacheSize = filtOptCacheSize;
  }

  public float getFiltOptThreshold() {
    return filtOptThreshold;
  }

  public void setFiltOptThreshold(float filtOptThreshold) {
    this.filtOptThreshold = filtOptThreshold;
  }

  public CacheConfig getFilterCacheConfig() {
    return filterCacheConfig;
  }

  public void setFilterCacheConfig(CacheConfig filterCacheConfig) {
    this.filterCacheConfig = filterCacheConfig;
  }

  public CacheConfig getQueryResultCacheConfig() {
    return queryResultCacheConfig;
  }

  public void setQueryResultCacheConfig(CacheConfig queryResultCacheConfig) {
    this.queryResultCacheConfig = queryResultCacheConfig;
  }

  public CacheConfig getDocumentCacheConfig() {
    return documentCacheConfig;
  }

  public void setDocumentCacheConfig(CacheConfig documentCacheConfig) {
    this.documentCacheConfig = documentCacheConfig;
  }

  public CacheConfig[] getUserCacheConfigs() {
    return userCacheConfigs;
  }

  public void setUserCacheConfigs(CacheConfig[] userCacheConfigs) {
    this.userCacheConfigs = userCacheConfigs;
  }

  public boolean isUseFilterForSortedQuery() {
    return useFilterForSortedQuery;
  }

  public void setUseFilterForSortedQuery(boolean useFilterForSortedQuery) {
    this.useFilterForSortedQuery = useFilterForSortedQuery;
  }

  public int getQueryResultWindowSize() {
    return queryResultWindowSize;
  }

  public void setQueryResultWindowSize(int queryResultWindowSize) {
    this.queryResultWindowSize = queryResultWindowSize;
  }

  public int getQueryResultMaxDocsCached() {
    return queryResultMaxDocsCached;
  }

  public void setQueryResultMaxDocsCached(int queryResultMaxDocsCached) {
    this.queryResultMaxDocsCached = queryResultMaxDocsCached;
  }

  public boolean isEnableLazyFieldLoading() {
    return enableLazyFieldLoading;
  }

  public void setEnableLazyFieldLoading(boolean enableLazyFieldLoading) {
    this.enableLazyFieldLoading = enableLazyFieldLoading;
  }

  public float getHashSetInverseLoadFactor() {
    return hashSetInverseLoadFactor;
  }

  public void setHashSetInverseLoadFactor(float hashSetInverseLoadFactor) {
    this.hashSetInverseLoadFactor = hashSetInverseLoadFactor;
  }

  public int getHashDocSetMaxSize() {
    return hashDocSetMaxSize;
  }

  public void setHashDocSetMaxSize(int hashDocSetMaxSize) {
    this.hashDocSetMaxSize = hashDocSetMaxSize;
  }

  public SolrIndexConfig getDefaultIndexConfig() {
    return defaultIndexConfig;
  }

  public void setDefaultIndexConfig(SolrIndexConfig defaultIndexConfig) {
    this.defaultIndexConfig = defaultIndexConfig;
  }

  public SolrIndexConfig getMainIndexConfig() {
    return mainIndexConfig;
  }

  public void setMainIndexConfig(SolrIndexConfig mainIndexConfig) {
    this.mainIndexConfig = mainIndexConfig;
  }

  public JmxConfiguration getJmxConfig() {
    return jmxConfig;
  }

  public void setJmxConfig(JmxConfiguration jmxConfig) {
    this.jmxConfig = jmxConfig;
  }

  public HttpCachingConfiguration getHttpCachingConfig() {
    return httpCachingConfig;
  }

  public void setHttpCachingConfig(HttpCachingConfiguration httpCachingConfig) {
    this.httpCachingConfig = httpCachingConfig;
  }

  public String getDataDir() {
    return dataDir;
  }

  public void setDataDir(String dataDir) {
    this.dataDir = dataDir;
  }

  public int getMaxWarmingSearchers() {
    return maxWarmingSearchers;
  }

  public void setMaxWarmingSearchers(int maxWarmingSearchers) {
    this.maxWarmingSearchers = maxWarmingSearchers;
  }

  public boolean isUseColdSearcher() {
    return useColdSearcher;
  }

  public void setUseColdSearcher(boolean useColdSearcher) {
    this.useColdSearcher = useColdSearcher;
  }

  public boolean isUnlockOnStartup() {
    return unlockOnStartup;
  }

  public void setUnlockOnStartup(boolean unlockOnStartup) {
    this.unlockOnStartup = unlockOnStartup;
  }

  public RequestDispatcherConfiguration getDispatcherConfig() {
    return dispatcherConfig;
  }

  public void setDispatcherConfig(RequestDispatcherConfiguration dispatcherConfig) {
    this.dispatcherConfig = dispatcherConfig;
  }  
}
