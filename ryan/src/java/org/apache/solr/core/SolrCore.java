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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.CommonParams.EchoParamStyle;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.config.CoreDescriptor;
import org.apache.solr.config.CoreInitalizer;
import org.apache.solr.config.CoreVars;
import org.apache.solr.config.SolrConfig;
import org.apache.solr.config.SolrConfiguraion;
import org.apache.solr.config.SolrResourceLoader;
import org.apache.solr.handler.component.*;
import org.apache.solr.highlight.SolrHighlighter;
import org.apache.solr.request.*;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.ValueSourceParser;
import org.apache.solr.update.DirectUpdateHandler2;
import org.apache.solr.update.SolrIndexWriter;
import org.apache.solr.update.UpdateHandler;
import org.apache.solr.update.processor.UpdateRequestProcessorChain;
import org.apache.solr.util.RefCounted;
import org.apache.solr.util.plugin.SolrCoreAware;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URL;


/**
 * @version $Id$
 */
public final class SolrCore implements SolrInfoMBean {
  public static final String version="1.0";  

  public static Logger log = LoggerFactory.getLogger(SolrCore.class);

  // TODO -- get rid of me...
  private final CoreInitalizer initalizer;
  private final SolrConfiguraion configuration;
  
  @Deprecated
  public static SolrCore INSTANCE;
  
  private String name;
  private String logid;

  private final IndexSchema schema;
  private final String dataDir;
  private final UpdateHandler updateHandler;
  
  private final RequestHandlers reqHandlers;
  private final SolrHighlighter highlighter;
  private final Map<String,SearchComponent> searchComponents;
  private final Map<String,UpdateRequestProcessorChain> updateProcessorChains;
  private final Map<String, SolrInfoMBean> infoRegistry;
  private final IndexDeletionPolicyWrapper solrDelPolicy;
  private final QueryResponseWriter defaultResponseWriter;

  private final Map<String, QueryResponseWriter> responseWriters;
  private final Map<String, QParserPlugin> qParserPlugins;
  private final HashMap<String, ValueSourceParser> valueSourceParsers;

  final List<SolrEventListener> firstSearcherListeners;
  final List<SolrEventListener> newSearcherListeners;
  
  
  private final long startTime = System.currentTimeMillis();


  private List<CloseHook> closeHooks = null;

  
  /**
   * The SolrResourceLoader used to load all resources for this core.
   * @since solr 1.3
   */
  @Deprecated
  public SolrResourceLoader getResourceLoader() {
    return initalizer.getSolrConfig().getResourceLoader();
  }
  
  /**
   * Gets the configuration object used by this core instance.
   */
  @Deprecated
  public SolrConfig getSolrConfig() {
    return initalizer.getSolrConfig();
  }

  
  /**
   * Gets the configuration object used by this core instance.
   */
  @Deprecated
  public CoreInitalizer getInitalizer() {
    return initalizer;
  }
  
  
  /**
   * Gets the schema object used by this core instance.
   */
  public IndexSchema getSchema() { 
    return schema;
  }
  
  public String getDataDir() {
    return dataDir;
  }
  
  public String getIndexDir() {
    return dataDir + "index/";
  }
  
  public String getName() {
    return name;
  }

  public void setName(String v) {
    this.name = v;
    this.logid = (v==null)?"":("["+v+"] ");
  }
  
  public String getLogId()
  {
    return this.logid;
  }

  /**
   * @return the Info Registry map which contains SolrInfoMBean objects keyed by name
   * @since solr 1.3
   */
  public Map<String, SolrInfoMBean> getInfoRegistry() {
    return infoRegistry;
  }


  
  /**
   * NOTE: this function is not thread safe.  However, it is safe to call within the
   * <code>inform( SolrCore core )</code> function for <code>SolrCoreAware</code> classes.
   * Outside <code>inform</code>, this could potentially throw a ConcurrentModificationException
   * 
   * @see SolrCoreAware
   */
  public void registerFirstSearcherListener( SolrEventListener listener )
  {
    firstSearcherListeners.add( listener );
  }

  /**
   * NOTE: this function is not thread safe.  However, it is safe to call within the
   * <code>inform( SolrCore core )</code> function for <code>SolrCoreAware</code> classes.
   * Outside <code>inform</code>, this could potentially throw a ConcurrentModificationException
   * 
   * @see SolrCoreAware
   */
  public void registerNewSearcherListener( SolrEventListener listener )
  {
    newSearcherListeners.add( listener );
  }

  /**
   * NOTE: this function is not thread safe.  However, it is safe to call within the
   * <code>inform( SolrCore core )</code> function for <code>SolrCoreAware</code> classes.
   * Outside <code>inform</code>, this could potentially throw a ConcurrentModificationException
   * 
   * @see SolrCoreAware
   */
  public QueryResponseWriter registerResponseWriter( String name, QueryResponseWriter responseWriter ){
    return responseWriters.put(name, responseWriter);
  }

  // gets a non-caching searcher
  public SolrIndexSearcher newSearcher(String name) throws IOException {
    return newSearcher(name, false);
  }
  
  // gets a non-caching searcher
  public SolrIndexSearcher newSearcher(String name, boolean readOnly) throws IOException {
    return new SolrIndexSearcher(this, schema, "main", IndexReader.open(FSDirectory.getDirectory(getIndexDir()), readOnly), true, false);
  }



  
  /**
   * Creates a new core and register it in the list of cores.
   * If a core with the same name already exists, it will be stopped and replaced by this one.
   *@param dataDir the index directory
   *@param config a solr config instance
   *@param schema a solr schema instance
   *
   *@since solr 1.3
   */
  public SolrCore(String name, CoreVars vars ) {
    INSTANCE = this;
    this.initalizer = vars.getInitalizer();
    this.configuration = vars.getConfiguration();
    this.setName( name );
    
    this.dataDir = configuration.getDataDir();
    log.info(logid+"Opening new SolrCore at: " + dataDir);

    
    //Initialize JMX
    this.schema = vars.getSchema();
    if (schema==null) {
      throw new NullPointerException( "schema can't be null" );
    }

    this.reqHandlers = vars.getReqHandlers();
    this.highlighter = vars.getHighlighter();
    this.searchComponents = vars.getSearchComponents();
    this.updateProcessorChains = vars.getUpdateProcessorChains();
    this.infoRegistry = vars.getInfoRegistry();
    this.solrDelPolicy = vars.getSolrDelPolicy();
    this.defaultResponseWriter = vars.getDefaultResponseWriter();

    this.responseWriters = vars.getResponseWriters();
    this.qParserPlugins = vars.getQParserPlugins();
    this.valueSourceParsers = vars.getValueSourceParsers();

    this.firstSearcherListeners = vars.getFirstSearcherListeners();
    this.newSearcherListeners = vars.getNewSearcherListeners();
    
    initIndex();
    
    final CountDownLatch latch = new CountDownLatch(1);

    try {
      // cause the executor to stall so firstSearcher events won't fire
      // until after inform() has been called for all components.
      // searchExecutor must be single-threaded for this to work
      searcherExecutor.submit(new Callable() {
        public Object call() throws Exception {
          latch.await();
          return null;
        }
      });

      // Open the searcher *before* the update handler so we don't end up opening
      // one in the middle.
      // With lockless commits in Lucene now, this probably shouldn't be an issue anymore
      getSearcher(false,false,null);

      // HACK -- for now, this is hard coded since it needs the update handler...
      updateHandler = new DirectUpdateHandler2(this, 
          vars.getCommitCallbacks(), vars.getOptimizeCallbacks() );
      infoRegistry.put("updateHandler", updateHandler);
    } 
    catch (IOException e) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, e);
    } 
    finally {
      // allow firstSearcher events to fire
      latch.countDown();
    }
    infoRegistry.put("core", this);
  }


  // protect via synchronized(SolrCore.class)
  private static Set<String> dirs = new HashSet<String>();
  
  // currently only called with SolrCore.class lock held
  void initIndex() {
    try {
      File dirFile = new File(getIndexDir());
      boolean indexExists = dirFile.canRead();
      boolean firstTime = dirs.add(dirFile.getCanonicalPath());
      if (indexExists && firstTime && configuration.isUnlockOnStartup() ) {
        // to remove locks, the directory must already exist... so we create it
        // if it didn't exist already...
        Directory dir = SolrIndexWriter.getDirectory(getIndexDir(), configuration.getMainIndexConfig() );
        if (dir != null && IndexWriter.isLocked(dir)) {
          log.warn(logid+"WARNING: Solr index directory '" + getIndexDir() + "' is locked.  Unlocking...");
          IndexWriter.unlock(dir);
        }
      }

      // Create the index if it doesn't exist.
      if(!indexExists) {
        log.warn(logid+"Solr index directory '" + dirFile + "' doesn't exist."
                + " Creating new index...");

        SolrIndexWriter writer = new SolrIndexWriter(
            "SolrCore.initIndex",getIndexDir(), true, schema, 
            configuration.getMainIndexConfig() );
        writer.close();
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  
  
  /**
   * @return an update processor registered to the given name.  Throw an exception if this chain is undefined
   */    
  public UpdateRequestProcessorChain getUpdateProcessingChain( final String name )
  {
    UpdateRequestProcessorChain chain = updateProcessorChains.get( name );
    if( chain == null ) {
      throw new SolrException( SolrException.ErrorCode.BAD_REQUEST,
          "unknown UpdateRequestProcessorChain: "+name );
    }
    return chain;
  }
  
  // this core current usage count
  private final AtomicInteger refCount = new AtomicInteger(1);

  final void open() {
    refCount.incrementAndGet();
  }
  
  /**
   * Close all resources allocated by the core...
   * <ul>
   *   <li>searcher</li>
   *   <li>updateHandler</li>
   *   <li>all CloseHooks will be notified</li>
   *   <li>All MBeans will be unregistered from MBeanServer if JMX was enabled
   *       </li>
   * </ul>
   * <p>
   * This should always be called when the core is obtained through {@link CoreContainer#getCore} or {@link CoreContainer#getAdminCore}
   * </p>
   * <p>
   * The actual close is performed if the core usage count is 1.
   * (A core is created with a usage count of 1).
   * If usage count is > 1, the usage count is decreased by 1.
   * If usage count is &lt; 0, this is an error and a runtime exception 
   * is thrown.
   * </p>
   */
  public void close() {
    int count = refCount.decrementAndGet();
    if (count > 0) return;
    if (count < 0) {
      //throw new RuntimeException("Too many closes on " + this);
      log.error("Too many close {count:"+count+"} on " + this + ". Please report this exception to solr-user@lucene.apache.org");
      return;
    }
    log.info(logid+" CLOSING SolrCore " + this);
    try {
      infoRegistry.clear();
    } catch (Exception e) {
      SolrException.log(log, e);
    }
    try {
      closeSearcher();
    } catch (Exception e) {
      SolrException.log(log,e);
    }
    try {
      searcherExecutor.shutdown();
    } catch (Exception e) {
      SolrException.log(log,e);
    }
    try {
      updateHandler.close();
    } catch (Exception e) {
      SolrException.log(log,e);
    }
    if( closeHooks != null ) {
       for( CloseHook hook : closeHooks ) {
         hook.close( this );
      }
    }
  }

  /** Current core usage count. */
  public int getOpenCount() {
    return refCount.get();
  }
  
  /** Whether this core is closed. */
  public boolean isClosed() {
      return refCount.get() <= 0;
  }
  
  @Override
  protected void finalize() {
    if (getOpenCount() != 0) {
      log.error("REFCOUNT ERROR: unreferenced " + this + " (" + getName() + ") has a reference count of " + getOpenCount());
    }
  }

   /**
    * Add a close callback hook
    */
   public void addCloseHook( CloseHook hook )
   {
     if( closeHooks == null ) {
       closeHooks = new ArrayList<CloseHook>();
     }
     closeHooks.add( hook );
   }

  ////////////////////////////////////////////////////////////////////////////////
  // Request Handler
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Get the request handler registered to a given name.  
   * 
   * This function is thread safe.
   */
  public SolrRequestHandler getRequestHandler(String handlerName) {
    return reqHandlers.get(handlerName);
  }
  
  /**
   * Returns an unmodifieable Map containing the registered handlers
   */
  public Map<String,SolrRequestHandler> getRequestHandlers() {
    return reqHandlers.getRequestHandlers();
  }

  /**
   * Get the SolrHighlighter
   */
  public SolrHighlighter getHighlighter() {
    return highlighter;
  }

  /**
   * Registers a handler at the specified location.  If one exists there, it will be replaced.
   * To remove a handler, register <code>null</code> at its path
   * 
   * Once registered the handler can be accessed through:
   * <pre>
   *   http://${host}:${port}/${context}/${handlerName}
   * or:  
   *   http://${host}:${port}/${context}/select?qt=${handlerName}
   * </pre>  
   * 
   * Handlers <em>must</em> be initalized before getting registered.  Registered
   * handlers can immediatly accept requests.
   * 
   * This call is thread safe.
   *  
   * @return the previous <code>SolrRequestHandler</code> registered to this name <code>null</code> if none.
   */
  public SolrRequestHandler registerRequestHandler(String handlerName, SolrRequestHandler handler) {
    return reqHandlers.register(handlerName,handler);
  }
  
  
  /**
   * @return a Search Component registered to a given name.  Throw an exception if the component is undefined
   */
  public SearchComponent getSearchComponent( String name )
  {
    SearchComponent component = searchComponents.get( name );
    if( component == null ) {
      throw new SolrException( SolrException.ErrorCode.BAD_REQUEST,
          "Unknown Search Component: "+name );
    }
    return component;
  }

  /**
   * Accessor for all the Search Components
   * @return An unmodifiable Map of Search Components
   */
  public Map<String, SearchComponent> getSearchComponents() {
    return Collections.unmodifiableMap(searchComponents);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // Update Handler
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * RequestHandlers need access to the updateHandler so they can all talk to the
   * same RAM indexer.  
   */
  public UpdateHandler getUpdateHandler() {
    return updateHandler;
  }

  ////////////////////////////////////////////////////////////////////////////////
  // Searcher Control
  ////////////////////////////////////////////////////////////////////////////////

  // The current searcher used to service queries.
  // Don't access this directly!!!! use getSearcher() to
  // get it (and it will increment the ref count at the same time)
  private RefCounted<SolrIndexSearcher> _searcher;

  // All of the open searchers.  Don't access this directly.
  // protected by synchronizing on searcherLock.
  private final LinkedList<RefCounted<SolrIndexSearcher>> _searchers = new LinkedList<RefCounted<SolrIndexSearcher>>();

  final ExecutorService searcherExecutor = Executors.newSingleThreadExecutor();
  private int onDeckSearchers;  // number of searchers preparing
  private Object searcherLock = new Object();  // the sync object for the searcher

  /**
  * Return a registered {@link RefCounted}&lt;{@link SolrIndexSearcher}&gt; with
  * the reference count incremented.  It <b>must</b> be decremented when no longer needed.
  * This method should not be called from SolrCoreAware.inform() since it can result
  * in a deadlock if useColdSearcher==false. 
  */
  public RefCounted<SolrIndexSearcher> getSearcher() {
    try {
      return getSearcher(false,true,null);
    } catch (IOException e) {
      SolrException.log(log,null,e);
      return null;
    }
  }

  /**
  * Return the newest {@link RefCounted}&lt;{@link SolrIndexSearcher}&gt; with
  * the reference count incremented.  It <b>must</b> be decremented when no longer needed.
  * If no searcher is currently open, then if openNew==true a new searcher will be opened,
  * or null is returned if openNew==false.
  */
  public RefCounted<SolrIndexSearcher> getNewestSearcher(boolean openNew) {
    synchronized (searcherLock) {
      if (_searchers.isEmpty()) {
        if (!openNew) return null;
        // Not currently implemented since simply calling getSearcher during inform()
        // can result in a deadlock.  Right now, solr always opens a searcher first
        // before calling inform() anyway, so this should never happen.
        throw new UnsupportedOperationException();
      }
      RefCounted<SolrIndexSearcher> newest = _searchers.getLast();
      newest.incref();
      return newest;
    }
  }


  /**
   * Get a {@link SolrIndexSearcher} or start the process of creating a new one.
   * <p>
   * The registered searcher is the default searcher used to service queries.
   * A searcher will normally be registered after all of the warming
   * and event handlers (newSearcher or firstSearcher events) have run.
   * In the case where there is no registered searcher, the newly created searcher will
   * be registered before running the event handlers (a slow searcher is better than no searcher).
   *
   * <p>
   * These searchers contain read-only IndexReaders. To access a non read-only IndexReader,
   * see newSearcher(String name, boolean readOnly).
   *
   * <p>
   * If <tt>forceNew==true</tt> then
   *  A new searcher will be opened and registered regardless of whether there is already
   *    a registered searcher or other searchers in the process of being created.
   * <p>
   * If <tt>forceNew==false</tt> then:<ul>
   *   <li>If a searcher is already registered, that searcher will be returned</li>
   *   <li>If no searcher is currently registered, but at least one is in the process of being created, then
   * this call will block until the first searcher is registered</li>
   *   <li>If no searcher is currently registered, and no searchers in the process of being registered, a new
   * searcher will be created.</li>
   * </ul>
   * <p>
   * If <tt>returnSearcher==true</tt> then a {@link RefCounted}&lt;{@link SolrIndexSearcher}&gt; will be returned with
   * the reference count incremented.  It <b>must</b> be decremented when no longer needed.
   * <p>
   * If <tt>waitSearcher!=null</tt> and a new {@link SolrIndexSearcher} was created,
   * then it is filled in with a Future that will return after the searcher is registered.  The Future may be set to
   * <tt>null</tt> in which case the SolrIndexSearcher created has already been registered at the time
   * this method returned.
   * <p>
   * @param forceNew           if true, force the open of a new index searcher regardless if there is already one open.
   * @param returnSearcher     if true, returns a {@link SolrIndexSearcher} holder with the refcount already incremented.
   * @param waitSearcher       if non-null, will be filled in with a {@link Future} that will return after the new searcher is registered.
   * @throws IOException
   */
  public RefCounted<SolrIndexSearcher> getSearcher(boolean forceNew, boolean returnSearcher, final Future[] waitSearcher) throws IOException {
    // it may take some time to open an index.... we may need to make
    // sure that two threads aren't trying to open one at the same time
    // if it isn't necessary.

    synchronized (searcherLock) {
      // see if we can return the current searcher
      if (_searcher!=null && !forceNew) {
        if (returnSearcher) {
          _searcher.incref();
          return _searcher;
        } else {
          return null;
        }
      }

      // check to see if we can wait for someone else's searcher to be set
      if (onDeckSearchers>0 && !forceNew && _searcher==null) {
        try {
          searcherLock.wait();
        } catch (InterruptedException e) {
          log.info(SolrException.toStr(e));
        }
      }

      // check again: see if we can return right now
      if (_searcher!=null && !forceNew) {
        if (returnSearcher) {
          _searcher.incref();
          return _searcher;
        } else {
          return null;
        }
      }

      // At this point, we know we need to open a new searcher...
      // first: increment count to signal other threads that we are
      //        opening a new searcher.
      onDeckSearchers++;
      if (onDeckSearchers < 1) {
        // should never happen... just a sanity check
        log.error(logid+"ERROR!!! onDeckSearchers is " + onDeckSearchers);
        onDeckSearchers=1;  // reset
      } else if (onDeckSearchers > configuration.getMaxWarmingSearchers() ) {
        onDeckSearchers--;
        String msg="Error opening new searcher. exceeded limit of maxWarmingSearchers="+configuration.getMaxWarmingSearchers() + ", try again later.";
        log.warn(logid+""+ msg);
        // HTTP 503==service unavailable, or 409==Conflict
        throw new SolrException(SolrException.ErrorCode.SERVICE_UNAVAILABLE,msg,true);
      } else if (onDeckSearchers > 1) {
        log.info(logid+"PERFORMANCE WARNING: Overlapping onDeckSearchers=" + onDeckSearchers);
      }
    }

    // open the index synchronously
    // if this fails, we need to decrement onDeckSearchers again.
    SolrIndexSearcher tmp;
    RefCounted<SolrIndexSearcher> newestSearcher = null;

    try {
      newestSearcher = getNewestSearcher(false);
      if (newestSearcher != null) {
        IndexReader currentReader = newestSearcher.get().getReader();
        IndexReader newReader = currentReader.reopen();

        if(newReader == currentReader) {
          currentReader.incRef();
        }
        
        tmp = new SolrIndexSearcher(this, schema, "main", newReader, true, true);
      } else {
        tmp = new SolrIndexSearcher(this, schema, "main", IndexReader.open(FSDirectory.getDirectory(getIndexDir()), true), true, true);
      }
    } catch (Throwable th) {
      synchronized(searcherLock) {
        onDeckSearchers--;
        // notify another waiter to continue... it may succeed
        // and wake any others.
        searcherLock.notify();
      }
      // need to close the searcher here??? we shouldn't have to.
      throw new RuntimeException(th);
    } finally {
      if (newestSearcher != null) {
        newestSearcher.decref();
      }
    }
    
    final SolrIndexSearcher newSearcher=tmp;

    RefCounted<SolrIndexSearcher> currSearcherHolder=null;
    final RefCounted<SolrIndexSearcher> newSearchHolder=newHolder(newSearcher);

    if (returnSearcher) newSearchHolder.incref();

    // a signal to decrement onDeckSearchers if something goes wrong.
    final boolean[] decrementOnDeckCount=new boolean[1];
    decrementOnDeckCount[0]=true;

    try {

      boolean alreadyRegistered = false;
      synchronized (searcherLock) {
        _searchers.add(newSearchHolder);
       
        if (_searcher == null) {
          // if there isn't a current searcher then we may
          // want to register this one before warming is complete instead of waiting.
          if ( configuration.isUseColdSearcher() ) {
            registerSearcher(newSearchHolder);
            decrementOnDeckCount[0]=false;
            alreadyRegistered=true;
          }
        } else {
          // get a reference to the current searcher for purposes of autowarming.
          currSearcherHolder=_searcher;
          currSearcherHolder.incref();
        }
      }


      final SolrIndexSearcher currSearcher = currSearcherHolder==null ? null : currSearcherHolder.get();

      //
      // Note! if we registered the new searcher (but didn't increment it's
      // reference count because returnSearcher==false, it's possible for
      // someone else to register another searcher, and thus cause newSearcher
      // to close while we are warming.
      //
      // Should we protect against that by incrementing the reference count?
      // Maybe we should just let it fail?   After all, if returnSearcher==false
      // and newSearcher has been de-registered, what's the point of continuing?
      //

      Future future=null;

      // warm the new searcher based on the current searcher.
      // should this go before the other event handlers or after?
      if (currSearcher != null) {
        future = searcherExecutor.submit(
                new Callable() {
                  public Object call() throws Exception {
                    try {
                      newSearcher.warm(currSearcher);
                    } catch (Throwable e) {
                      SolrException.logOnce(log,null,e);
                    }
                    return null;
                  }
                }
        );
      }
      
      if (currSearcher==null && firstSearcherListeners.size() > 0) {
        future = searcherExecutor.submit(
                new Callable() {
                  public Object call() throws Exception {
                    try {
                      for (SolrEventListener listener : firstSearcherListeners) {
                        listener.newSearcher(newSearcher,null);
                      }
                    } catch (Throwable e) {
                      SolrException.logOnce(log,null,e);
                    }
                    return null;
                  }
                }
        );
      }

      if (currSearcher!=null && newSearcherListeners.size() > 0) {
        future = searcherExecutor.submit(
                new Callable() {
                  public Object call() throws Exception {
                    try {
                      for (SolrEventListener listener : newSearcherListeners) {
                        listener.newSearcher(newSearcher, currSearcher);
                      }
                    } catch (Throwable e) {
                      SolrException.logOnce(log,null,e);
                    }
                    return null;
                  }
                }
        );
      }

      // WARNING: this code assumes a single threaded executor (that all tasks
      // queued will finish first).
      final RefCounted<SolrIndexSearcher> currSearcherHolderF = currSearcherHolder;
      if (!alreadyRegistered) {
        future = searcherExecutor.submit(
                new Callable() {
                  public Object call() throws Exception {
                    try {
                      // signal that we no longer need to decrement
                      // the count *before* registering the searcher since
                      // registerSearcher will decrement even if it errors.
                      decrementOnDeckCount[0]=false;
                      registerSearcher(newSearchHolder);
                    } catch (Throwable e) {
                      SolrException.logOnce(log,null,e);
                    } finally {
                      // we are all done with the old searcher we used
                      // for warming...
                      if (currSearcherHolderF!=null) currSearcherHolderF.decref();
                    }
                    return null;
                  }
                }
        );
      }

      if (waitSearcher != null) {
        waitSearcher[0] = future;
      }

      // Return the searcher as the warming tasks run in parallel
      // callers may wait on the waitSearcher future returned.
      return returnSearcher ? newSearchHolder : null;

    } catch (Exception e) {
      SolrException.logOnce(log,null,e);
      if (currSearcherHolder != null) currSearcherHolder.decref();

      synchronized (searcherLock) {
        if (decrementOnDeckCount[0]) {
          onDeckSearchers--;
        }
        if (onDeckSearchers < 0) {
          // sanity check... should never happen
          log.error(logid+"ERROR!!! onDeckSearchers after decrement=" + onDeckSearchers);
          onDeckSearchers=0; // try and recover
        }
        // if we failed, we need to wake up at least one waiter to continue the process
        searcherLock.notify();
      }

      // since the indexreader was already opened, assume we can continue on
      // even though we got an exception.
      return returnSearcher ? newSearchHolder : null;
    }

  }


  private RefCounted<SolrIndexSearcher> newHolder(SolrIndexSearcher newSearcher) {
    RefCounted<SolrIndexSearcher> holder = new RefCounted<SolrIndexSearcher>(newSearcher) {
      @Override
      public void close() {
        try {
          synchronized(searcherLock) {
            // it's possible for someone to get a reference via the _searchers queue
            // and increment the refcount while RefCounted.close() is being called.
            // we check the refcount again to see if this has happened and abort the close.
            // This relies on the RefCounted class allowing close() to be called every
            // time the counter hits zero.
            if (refcount.get() > 0) return;
            _searchers.remove(this);
          }
          resource.close();
        } catch (IOException e) {
          log.error("Error closing searcher:" + SolrException.toStr(e));
        }
      }
    };
    holder.incref();  // set ref count to 1 to account for this._searcher
    return holder;
  }


  // Take control of newSearcherHolder (which should have a reference count of at
  // least 1 already.  If the caller wishes to use the newSearcherHolder directly
  // after registering it, then they should increment the reference count *before*
  // calling this method.
  //
  // onDeckSearchers will also be decremented (it should have been incremented
  // as a result of opening a new searcher).
  private void registerSearcher(RefCounted<SolrIndexSearcher> newSearcherHolder) throws IOException {
    synchronized (searcherLock) {
      try {
        if (_searcher != null) {
          _searcher.decref();   // dec refcount for this._searcher
          _searcher=null;
        }

        _searcher = newSearcherHolder;
        SolrIndexSearcher newSearcher = newSearcherHolder.get();

        newSearcher.register(); // register subitems (caches)
        log.info(logid+"Registered new searcher " + newSearcher);

      } catch (Throwable e) {
        log(e);
      } finally {
        // wake up anyone waiting for a searcher
        // even in the face of errors.
        onDeckSearchers--;
        searcherLock.notifyAll();
      }
    }
  }



  public void closeSearcher() {
    log.info(logid+"Closing main searcher on request.");
    synchronized (searcherLock) {
      if (_searcher != null) {
        _searcher.decref();   // dec refcount for this._searcher
        _searcher=null; // isClosed() does check this
        infoRegistry.remove("currentSearcher");
      }
    }
  }


  public void execute(SolrRequestHandler handler, SolrQueryRequest req, SolrQueryResponse rsp) {
    if (handler==null) {
      log.warn(logid+"Null Request Handler '" + req.getParams().get(CommonParams.QT) +"' :" + req);
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,"Null Request Handler '" + req.getParams().get(CommonParams.QT) + "'", true);
    }
    // setup response header and handle request
    final NamedList<Object> responseHeader = new SimpleOrderedMap<Object>();
    rsp.add("responseHeader", responseHeader);
    NamedList toLog = rsp.getToLog();
    //toLog.add("core", getName());
    toLog.add("webapp", req.getContext().get("webapp"));
    toLog.add("path", req.getContext().get("path"));
    toLog.add("params", "{" + req.getParamString() + "}");
    handler.handleRequest(req,rsp);
    setResponseHeaderValues(handler,req,rsp);
    StringBuilder sb = new StringBuilder();
    for (int i=0; i<toLog.size(); i++) {
     	String name = toLog.getName(i);
     	Object val = toLog.getVal(i);
     	sb.append(name).append("=").append(val).append(" ");
    }
    log.info(logid +  sb.toString());
    /*log.info(logid+"" + req.getContext().get("path") + " "
            + req.getParamString()+ " 0 "+
       (int)(rsp.getEndTime() - req.getStartTime()));*/
  }
  
  protected void setResponseHeaderValues(SolrRequestHandler handler, SolrQueryRequest req, SolrQueryResponse rsp) {
    // TODO should check that responseHeader has not been replaced by handler
	NamedList responseHeader = rsp.getResponseHeader();
    final int qtime=(int)(rsp.getEndTime() - req.getStartTime());
    responseHeader.add("status",rsp.getException()==null ? 0 : 500);
    responseHeader.add("QTime",qtime);
    rsp.getToLog().add("status",rsp.getException()==null ? 0 : 500);
    rsp.getToLog().add("QTime",qtime);
    
    SolrParams params = req.getParams();
    if( params.getBool(CommonParams.HEADER_ECHO_HANDLER, false) ) {
      responseHeader.add("handler", handler.getName() );
    }
    
    // Values for echoParams... false/true/all or false/explicit/all ???
    String ep = params.get( CommonParams.HEADER_ECHO_PARAMS, null );
    if( ep != null ) {
      EchoParamStyle echoParams = EchoParamStyle.get( ep );
      if( echoParams == null ) {
        throw new SolrException( SolrException.ErrorCode.BAD_REQUEST,"Invalid value '" + ep + "' for " + CommonParams.HEADER_ECHO_PARAMS 
            + " parameter, use '" + EchoParamStyle.EXPLICIT + "' or '" + EchoParamStyle.ALL + "'" );
      }
      if( echoParams == EchoParamStyle.EXPLICIT ) {
        responseHeader.add("params", req.getOriginalParams().toNamedList());
      } else if( echoParams == EchoParamStyle.ALL ) {
        responseHeader.add("params", req.getParams().toNamedList());
      }
    }
  }


  final public static void log(Throwable e) {
    SolrException.logOnce(log,null,e);
  }

  
  
  /** Finds a writer by name, or returns the default writer if not found. */
  public final QueryResponseWriter getQueryResponseWriter(String writerName) {
    if (writerName != null) {
        QueryResponseWriter writer = responseWriters.get(writerName);
        if (writer != null) {
            return writer;
        }
    }
    return defaultResponseWriter;
  }

  /** Returns the appropriate writer for a request. If the request specifies a writer via the
   * 'wt' parameter, attempts to find that one; otherwise return the default writer.
   */
  public final QueryResponseWriter getQueryResponseWriter(SolrQueryRequest request) {
    return getQueryResponseWriter(request.getParams().get(CommonParams.WT)); 
  }

  public QParserPlugin getQueryPlugin(String parserName) {
    QParserPlugin plugin = qParserPlugins.get(parserName);
    if (plugin != null) return plugin;
    throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unknown query parser '"+parserName+"'");
  }
  
  public ValueSourceParser getValueSourceParser(String parserName) {
    return valueSourceParsers.get(parserName);
  }

  @Deprecated
  public CoreDescriptor getCoreDescriptor() {
    return initalizer.getCoreDescriptor();
  }

  public IndexDeletionPolicyWrapper getDeletionPolicy(){
    return solrDelPolicy;
  }

  /////////////////////////////////////////////////////////////////////
  // SolrInfoMBean stuff: Statistics and Module Info
  /////////////////////////////////////////////////////////////////////

  public String getVersion() {
    return SolrCore.version;
  }

  public String getDescription() {
    return "SolrCore";
  }

  public Category getCategory() {
    return Category.CORE;
  }

  public String getSourceId() {
    return "$Id$";
  }

  public String getSource() {
    return "$URL$";
  }

  public URL[] getDocs() {
    return null;
  }

  public NamedList getStatistics() {
    NamedList lst = new SimpleOrderedMap();
    lst.add("coreName", name==null ? "(null)" : name);
    lst.add("startTime", new Date(startTime));
    lst.add("refCount", getOpenCount());
    lst.add("aliases", getCoreDescriptor().getCoreContainer().getCoreNames(this));
    return lst;
  }

  public long getStartTime() {
    return startTime;
  }

  public SolrConfiguraion getConfiguration() {
    return configuration;
  }
}



