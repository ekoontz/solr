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

import org.apache.solr.config.HttpCachingConfiguration.LastModFrom;
import org.apache.solr.search.CacheConfig;
import org.apache.lucene.search.BooleanQuery;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;


/**
 * Provides a static reference to a Config object modeling the main
 * configuration data for a a Solr instance -- typically found in
 * "solrconfig.xml".
 *
 * @version $Id$
 */
public class SolrConfig extends Config {

  public static final String DEFAULT_CONF_FILE = "solrconfig.xml";

  /**
   * Singleton keeping track of configuration errors
   */
  public static final Collection<Throwable> severeErrors = new HashSet<Throwable>();
  public final SolrConfiguraion vars = new SolrConfiguraion();

  /** Creates a default instance from the solrconfig.xml. */
  public SolrConfig()
  throws ParserConfigurationException, IOException, SAXException {
    this( (SolrResourceLoader) null, DEFAULT_CONF_FILE, null );
  }
  
  /** Creates a configuration instance from a configuration name.
   * A default resource loader will be created (@see SolrResourceLoader)
   *@param name the configuration name used by the loader
   */
  public SolrConfig(String name)
  throws ParserConfigurationException, IOException, SAXException {
    this( (SolrResourceLoader) null, name, null);
  }

  /** Creates a configuration instance from a configuration name and stream.
   * A default resource loader will be created (@see SolrResourceLoader).
   * If the stream is null, the resource loader will open the configuration stream.
   * If the stream is not null, no attempt to load the resource will occur (the name is not used).
   *@param name the configuration name
   *@param is the configuration stream
   */
  public SolrConfig(String name, InputStream is)
  throws ParserConfigurationException, IOException, SAXException {
    this( (SolrResourceLoader) null, name, is );
  }
  
  /** Creates a configuration instance from an instance directory, configuration name and stream.
   *@param instanceDir the directory used to create the resource loader
   *@param name the configuration name used by the loader if the stream is null
   *@param is the configuration stream 
   */
  public SolrConfig(String instanceDir, String name, InputStream is)
  throws ParserConfigurationException, IOException, SAXException {
    this(new SolrResourceLoader(instanceDir), name, is);
  }
  
   /** Creates a configuration instance from a resource loader, a configuration name and a stream.
   * If the stream is null, the resource loader will open the configuration stream.
   * If the stream is not null, no attempt to load the resource will occur (the name is not used).
   *@param loader the resource loader
   *@param name the configuration name
   *@param is the configuration stream
   */
  public SolrConfig(SolrResourceLoader loader, String name, InputStream is)
  throws ParserConfigurationException, IOException, SAXException {
    super(loader, name, is, "/config/");
    
    vars.defaultIndexConfig = new SolrIndexConfig(this, null, null);
    vars.mainIndexConfig = new SolrIndexConfig(this, "mainIndex", vars.defaultIndexConfig);
    
    vars.booleanQueryMaxClauseCount = getInt("query/maxBooleanClauses", BooleanQuery.getMaxClauseCount());
    vars.filtOptEnabled = getBool("query/boolTofilterOptimizer/@enabled", false);
    vars.filtOptCacheSize = getInt("query/boolTofilterOptimizer/@cacheSize",32);
    vars.filtOptThreshold = getFloat("query/boolTofilterOptimizer/@threshold",.05f);
    
    vars.useFilterForSortedQuery = getBool("query/useFilterForSortedQuery", false);
    vars.queryResultWindowSize = getInt("query/queryResultWindowSize", 1);
    vars.queryResultMaxDocsCached = getInt("query/queryResultMaxDocsCached", Integer.MAX_VALUE);
    vars.enableLazyFieldLoading = getBool("query/enableLazyFieldLoading", false);

    
    vars.filterCacheConfig = CacheConfig.getConfig(this, "query/filterCache");
    vars.queryResultCacheConfig = CacheConfig.getConfig(this, "query/queryResultCache");
    vars.documentCacheConfig = CacheConfig.getConfig(this, "query/documentCache");
    vars.userCacheConfigs = CacheConfig.getMultipleConfigs(this, "query/cache");

    vars.hashSetInverseLoadFactor = 1.0f / getFloat("//HashDocSet/@loadFactor",0.75f);
    vars.hashDocSetMaxSize= getInt("//HashDocSet/@maxSize",3000);
    
    vars.useColdSearcher = getBool("query/useColdSearcher",false);
    vars.maxWarmingSearchers = getInt("query/maxWarmingSearchers",Integer.MAX_VALUE);

    vars.unlockOnStartup = getBool("mainIndex/unlockOnStartup", false);
     
    RequestDispatcherConfiguration rdc = new RequestDispatcherConfiguration();
    rdc.uploadLimitKB = getInt( 
        "requestDispatcher/requestParsers/@multipartUploadLimitInKB", (int)rdc.uploadLimitKB );
    
    rdc.enableRemoteStreams = getBool( 
        "requestDispatcher/requestParsers/@enableRemoteStreaming", false ); 
    
    // Let this filter take care of /select?xxx format
    rdc.handleSelect = getBool( 
        "requestDispatcher/@handleSelect", rdc.handleSelect ); 
    vars.dispatcherConfig = rdc;
    
    
    // load HTTP
    vars.httpCachingConfig = loadHttpCachingConfig();
    
    Node jmx = getNode("jmx", false);
    if (jmx != null) {
      vars.jmxConfig = new JmxConfiguration(
          true, 
          get("jmx/@agentId", null), 
          get("jmx/@serviceUrl", null) );
    } else {
      vars.jmxConfig = new JmxConfiguration(false, null, null);
    }
    
    Config.log.info("Loaded SolrConfig: " + name);
  }
  
  HttpCachingConfiguration loadHttpCachingConfig()
  {
    /** config xpath prefix for getting HTTP Caching options */
    final String CACHE_PRE
      = "requestDispatcher/httpCaching/";
    
    /** For extracting Expires "ttl" from <cacheControl> config */
    final Pattern MAX_AGE
      = Pattern.compile("\\bmax-age=(\\d+)");

    
    HttpCachingConfiguration cfg = new HttpCachingConfiguration();

    cfg.never304 = this.getBool(CACHE_PRE+"@never304", false);
    cfg.etagSeed = this.get(CACHE_PRE+"@etagSeed", "Solr");
    cfg.lastModFrom = LastModFrom.parse( this.get(CACHE_PRE+"@lastModFrom","openTime") );
    cfg.cacheControlHeader = this.get(CACHE_PRE+"cacheControl",null);

    Long tmp = null; // maxAge
    if (null != cfg.cacheControlHeader) {
      try { 
        final Matcher ttlMatcher = MAX_AGE.matcher(cfg.cacheControlHeader);
        final String ttlStr = ttlMatcher.find() ? ttlMatcher.group(1) : null;
        tmp = (null != ttlStr && !"".equals(ttlStr))
          ? Long.valueOf(ttlStr)
          : null;
      } catch (Exception e) {
        log.warn( "Ignoring exception while attempting to " +
                  "extract max-age from cacheControl config: " +
                  cfg.cacheControlHeader, e);
      }
    }
    cfg.maxAge = tmp;
    return cfg;
  }
}
