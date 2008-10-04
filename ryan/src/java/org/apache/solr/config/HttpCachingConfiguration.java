package org.apache.solr.config;

import org.apache.solr.core.SolrCore;

public class HttpCachingConfiguration 
{
  public static enum LastModFrom {
    OPENTIME, DIRLASTMOD, BOGUS;

    /** Input must not be null */
    public static LastModFrom parse(final String s) {
      try {
        return valueOf(s.toUpperCase());
      } catch (Exception e) {
        SolrCore.log.warn( "Unrecognized value for lastModFrom: " + s, e);
        return BOGUS;
      }
    }
  }
  
  boolean never304;
  String etagSeed;
  String cacheControlHeader;
  Long maxAge;
  LastModFrom lastModFrom;
  
  public boolean isNever304() { return never304; }
  public String getEtagSeed() { return etagSeed; }
  /** null if no Cache-Control header */
  public String getCacheControlHeader() { return cacheControlHeader; }
  /** null if no max age limitation */
  public Long getMaxAge() { return maxAge; }
  public LastModFrom getLastModFrom() { return lastModFrom; }
}