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

package org.apache.solr.core;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryResponse;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.util.plugin.ResourceLoaderAware;
import org.apache.solr.util.plugin.SolrCoreAware;

/**
 */
public final class RequestHandlers {
  public static Logger log = LoggerFactory.getLogger(RequestHandlers.class);

  public static final String DEFAULT_HANDLER_NAME="standard";
  protected final Map<String, SolrInfoMBean> infoRegistry;
  // Use a synchronized map - since the handlers can be changed at runtime, 
  // the map implementation should be thread safe
  private final Map<String, SolrRequestHandler> handlers = Collections.synchronizedMap(
      new HashMap<String,SolrRequestHandler>() );

  /**
   * Trim the trailing '/' if its there.
   * 
   * we want:
   *  /update/csv
   *  /update/csv/
   * to map to the same handler 
   * 
   */
  private static String normalize( String p )
  {
    if( p != null && p.endsWith( "/" ) && p.length() > 1 )
      return p.substring( 0, p.length()-1 );
    
    return p;
  }
  
  public RequestHandlers(Map<String, SolrInfoMBean> infoRegistry) {
      this.infoRegistry = infoRegistry;
  }
  
  /**
   * @return the RequestHandler registered at the given name 
   */
  public SolrRequestHandler get(String handlerName) {
    return handlers.get(normalize(handlerName));
  }

  /**
   * Handlers must be initialized before calling this function.  As soon as this is
   * called, the handler can immediately accept requests.
   * 
   * This call is thread safe.
   * 
   * @return the previous handler at the given path or null
   */
  public SolrRequestHandler register( String handlerName, SolrRequestHandler handler ) {
    String norm = normalize( handlerName );
    if( handler == null ) {
      return handlers.remove( norm );
    }
    SolrRequestHandler old = handlers.put(norm, handler);
    if (handlerName != null && handlerName != "") {
      if (handler instanceof SolrInfoMBean) {
        infoRegistry.put(handlerName, handler);
      }
    }
    return old;
  }

  /**
   * Returns an unmodifiable Map containing the registered handlers
   */
  public Map<String,SolrRequestHandler> getRequestHandlers() {
    return Collections.unmodifiableMap( handlers );
  }
  

  /**
   * The <code>LazyRequestHandlerWrapper</core> wraps any {@link SolrRequestHandler}.  
   * Rather then instanciate and initalize the handler on startup, this wrapper waits
   * until it is actually called.  This should only be used for handlers that are
   * unlikely to be used in the normal lifecycle.
   * 
   * You can enable lazy loading in solrconfig.xml using:
   * 
   * <pre>
   *  &lt;requestHandler name="..." class="..." startup="lazy"&gt;
   *    ...
   *  &lt;/requestHandler&gt;
   * </pre>
   * 
   * This is a private class - if there is a real need for it to be public, it could
   * move
   * 
   * @version $Id$
   * @since solr 1.2
   */
  private static final class LazyRequestHandlerWrapper implements SolrRequestHandler, SolrInfoMBean
  {
    private final SolrCore core;
    private String _className;
    private NamedList _args;
    private SolrRequestHandler _handler;
    
    public LazyRequestHandlerWrapper( SolrCore core, String className, NamedList args )
    {
      this.core = core;
      _className = className;
      _args = args;
      _handler = null; // don't initialize
    }
    
    /**
     * In normal use, this function will not be called
     */
    public void init(NamedList args) {
      // do nothing
    }
    
    /**
     * Wait for the first request before initializing the wrapped handler 
     */
    public void handleRequest(SolrQueryRequest req, SolrQueryResponse rsp)  {
      getWrappedHandler().handleRequest( req, rsp );
    }

    public synchronized SolrRequestHandler getWrappedHandler() 
    {
      if( _handler == null ) {
        try {
          _handler = core.getInitalizer().createRequestHandler(_className);
          _handler.init( _args );
          
          if( _handler instanceof ResourceLoaderAware ) {
            ((ResourceLoaderAware)_handler).inform( core.getSolrConfig().getResourceLoader() );
          }
          
          if( _handler instanceof SolrCoreAware ) {
            ((SolrCoreAware)_handler).inform( core );
          }
        }
        catch( Exception ex ) {
          throw new SolrException( SolrException.ErrorCode.SERVER_ERROR, "lazy loading error", ex );
        }
      }
      return _handler; 
    }

    public String getHandlerClass()
    {
      return _className;
    }
    
    //////////////////////// SolrInfoMBeans methods //////////////////////

    public String getName() {
      return "Lazy["+_className+"]";
    }

    public String getDescription()
    {
      if( _handler == null ) {
        return getName();
      }
      return _handler.getDescription();
    }
    
    public String getVersion() {
        String rev = "$Revision$";
        if( _handler != null ) {
          rev += " :: " + _handler.getVersion();
        }
        return rev;
    }

    public String getSourceId() {
      String rev = "$Id$";
      if( _handler != null ) {
        rev += " :: " + _handler.getSourceId();
      }
      return rev;
    }

    public String getSource() {
      String rev = "$URL$";
      if( _handler != null ) {
        rev += "\n" + _handler.getSource();
      }
      return rev;
    }
      
    public URL[] getDocs() {
      if( _handler == null ) {
        return null;
      }
      return _handler.getDocs();
    }

    public Category getCategory()
    {
      return Category.QUERYHANDLER;
    }

    public NamedList getStatistics() {
      if( _handler != null ) {
        return _handler.getStatistics();
      }
      NamedList<String> lst = new SimpleOrderedMap<String>();
      lst.add("note", "not initialized yet" );
      return lst;
    }
  }
}







