package org.apache.solr.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.search.BooleanQuery;
import org.apache.solr.common.ResourceLoader;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.DOMUtil;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.IndexDeletionPolicyWrapper;
import org.apache.solr.core.JmxMonitoredMap;
import org.apache.solr.core.RequestHandlers;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrDeletionPolicy;
import org.apache.solr.core.SolrEventListener;
import org.apache.solr.core.SolrInfoMBean;
import org.apache.solr.handler.StandardRequestHandler;
import org.apache.solr.handler.component.DebugComponent;
import org.apache.solr.handler.component.FacetComponent;
import org.apache.solr.handler.component.HighlightComponent;
import org.apache.solr.handler.component.MoreLikeThisComponent;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.highlight.DefaultSolrHighlighter;
import org.apache.solr.highlight.SolrHighlighter;
import org.apache.solr.request.BinaryResponseWriter;
import org.apache.solr.request.JSONResponseWriter;
import org.apache.solr.request.PythonResponseWriter;
import org.apache.solr.request.QueryResponseWriter;
import org.apache.solr.request.RawResponseWriter;
import org.apache.solr.request.RubyResponseWriter;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.request.XMLResponseWriter;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.ValueSourceParser;
import org.apache.solr.update.UpdateHandler;
import org.apache.solr.update.processor.LogUpdateProcessorFactory;
import org.apache.solr.update.processor.RunUpdateProcessorFactory;
import org.apache.solr.update.processor.UpdateRequestProcessorChain;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;
import org.apache.solr.util.plugin.AbstractPluginLoader;
import org.apache.solr.util.plugin.NamedListInitializedPlugin;
import org.apache.solr.util.plugin.NamedListPluginLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class CoreInitalizer 
{
  public static Logger log = LoggerFactory.getLogger(CoreInitalizer.class);
  private final SolrConfig solrConfig;
  private final CoreDescriptor coreDescriptor;
  private final CoreVars vars;
  
  public CoreInitalizer(String dataDir, IndexSchema schema) throws ParserConfigurationException, IOException, SAXException {
    this(null, dataDir, new SolrConfig(), schema, null );
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
  public CoreInitalizer(String name, String dataDir, SolrConfig config, IndexSchema schema, CoreDescriptor cd) 
  {
    vars = new CoreVars();
    vars.initalizer = this;
    this.solrConfig = config;
    vars.configuration = config.vars;
    this.coreDescriptor = cd;

    SolrResourceLoader loader = config.getResourceLoader();
    if (dataDir == null)
      dataDir = config.get("dataDir",loader.getInstanceDir()+"data/");
    vars.configuration.dataDir = SolrResourceLoader.normalizeDir(dataDir);
    

//    updateHandler = createUpdateHandler(
//      solrConfig.get("updateHandler/@class", DirectUpdateHandler2.class.getName())
//    );

    if (vars.configuration.getJmxConfig().enabled) {
      vars.infoRegistry = new JmxMonitoredMap<String, SolrInfoMBean>(name, vars.configuration.getJmxConfig() );
    } 
    else  {
      log.info("JMX monitoring not detected for core: " + name);
      vars.infoRegistry = new LinkedHashMap<String, SolrInfoMBean>();
    }

    vars.highlighter = createHighlighter(
        solrConfig.get("highlighting/@class", DefaultSolrHighlighter.class.getName())
    );
    vars.highlighter.initalize( solrConfig );
    
    if( schema == null ) {
      vars.schema = new IndexSchema(config, config.vars, IndexSchema.DEFAULT_SCHEMA_FILE, null);      
    }
    else {
      vars.schema = schema;
    }
    
    
    // set the cache regenerators...
    org.apache.solr.search.SolrIndexSearcher.initRegenerators(vars.configuration);
    
    vars.reqHandlers = initHandlersFromConfig(config);

    initWriters();
    initQParsers();
    initValueSourceParsers();
    parseListeners();
    vars.solrDelPolicy = initDeletionPolicy();
    parseEventListeners();
    
    vars.searchComponents = loadSearchComponents( config );

    // Processors initialized before the handlers
    vars.updateProcessorChains = loadUpdateProcessorChains();
    
  }
  
  public SolrCore initalizeCore( String name )
  {
    SolrCore core = new SolrCore( name, vars );
    
    // now go through and inform everything...

    // Finally tell anyone who wants to know
    SolrResourceLoader loader = solrConfig.getResourceLoader();
    loader.inform( loader );
    loader.inform( core );
    
    return core;
  }
  
  

  static int boolean_query_max_clause_count = Integer.MIN_VALUE;
  // only change the BooleanQuery maxClauseCount once for ALL cores...
  void booleanQueryMaxClauseCount()  {
    synchronized(CoreVars.class) {
      if (boolean_query_max_clause_count == Integer.MIN_VALUE) {
        boolean_query_max_clause_count = vars.configuration.booleanQueryMaxClauseCount;
        BooleanQuery.setMaxClauseCount(boolean_query_max_clause_count);
      } else if (boolean_query_max_clause_count != vars.configuration.booleanQueryMaxClauseCount ) {
        log.debug("BooleanQuery.maxClauseCount= " +boolean_query_max_clause_count+ 
            ", ignoring " +vars.configuration.booleanQueryMaxClauseCount);
      }
    }
  }
  

  /** Creates an instance by trying a constructor that accepts a SolrCore before
   *  trying the default (no arg) constructor.
   *@param className the instance class to create
   *@cast the class or interface that the instance should extend or implement
   *@param msg a message helping compose the exception error if any occurs.
   *@return the desired instance
   *@throws SolrException if the object could not be instantiated
   */
  private <T extends Object> T createInstance(String className, Class<T> cast, String msg) {
    Class clazz = null;
    if (msg == null) msg = "SolrCore Object";
    try {
      try {
        clazz = solrConfig.getResourceLoader().findClass(className);
        if (cast != null && !cast.isAssignableFrom(clazz))
          throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,"Error Instantiating "+msg+", "+className+ " is not a " +cast.getName());
        
        java.lang.reflect.Constructor cons = clazz.getConstructor(new Class[]{SolrCore.class});
        return (T) cons.newInstance(new Object[]{this});
      } catch(NoSuchMethodException xnomethod) {
        return (T) clazz.newInstance();
      }
    } catch (SolrException e) {
      throw e;
    } catch (Exception e) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,"Error Instantiating "+msg+", "+className+ " failed to instantiate " +cast.getName(), e);
    }
  }

  public SolrEventListener createEventListener(String className) {
    return createInstance(className, SolrEventListener.class, "Event Listener");
  }

  public SolrRequestHandler createRequestHandler(String className) {
    return createInstance(className, SolrRequestHandler.class, "Request Handler");
  }

  private UpdateHandler createUpdateHandler(String className) {
    return createInstance(className, UpdateHandler.class, "Update Handler");
  }
  
  private SolrHighlighter createHighlighter(String className) {
    return createInstance(className, SolrHighlighter.class, "Highlighter");
  }
  

  /**
   * Load the request processors configured in solrconfig.xml
   */
  private Map<String,UpdateRequestProcessorChain> loadUpdateProcessorChains() {
    final Map<String,UpdateRequestProcessorChain> map = new HashMap<String, UpdateRequestProcessorChain>();
    
    final String parsingErrorText = "Parsing Update Request Processor Chain";
    UpdateRequestProcessorChain def = null;
    
    // This is kinda ugly, but at least it keeps the xpath logic in one place
    // away from the Processors themselves.  
    XPath xpath = solrConfig.getXPath();
    NodeList nodes = (NodeList)solrConfig.evaluate("updateRequestProcessorChain", XPathConstants.NODESET);
    boolean requireName = nodes.getLength() > 1;
    if (nodes !=null ) {
      for (int i=0; i<nodes.getLength(); i++) {
        Node node = nodes.item(i);
        String name       = DOMUtil.getAttr(node,"name", requireName?parsingErrorText:null);
        boolean isDefault = "true".equals( DOMUtil.getAttr(node,"default", null ) );
        
        NodeList links = null;
        try {
          links = (NodeList)xpath.evaluate("processor", node, XPathConstants.NODESET);
        } 
        catch (XPathExpressionException e) {
          throw new SolrException( SolrException.ErrorCode.SERVER_ERROR,"Error reading processors",e,false);
        }
        if( links == null || links.getLength() < 1 ) {
          throw new RuntimeException( "updateRequestProcessorChain require at least one processor");
        }
        
        // keep a list of the factories...
        final ArrayList<UpdateRequestProcessorFactory> factories = new ArrayList<UpdateRequestProcessorFactory>(links.getLength());
        // Load and initialize the plugin chain
        AbstractPluginLoader<UpdateRequestProcessorFactory> loader 
            = new AbstractPluginLoader<UpdateRequestProcessorFactory>( "processor chain", false, false ) {
          @Override
          protected void init(UpdateRequestProcessorFactory plugin, Node node) throws Exception {
            plugin.init( (node==null)?null:DOMUtil.childNodesToNamedList(node) );
          }
    
          @Override
          protected UpdateRequestProcessorFactory register(String name, UpdateRequestProcessorFactory plugin) throws Exception {
            factories.add( plugin );
            return null;
          }
        };
        loader.load( solrConfig.getResourceLoader(), links );
        
        
        UpdateRequestProcessorChain chain = new UpdateRequestProcessorChain( 
            factories.toArray( new UpdateRequestProcessorFactory[factories.size()] ) );
        if( isDefault || nodes.getLength()==1 ) {
          def = chain;
        }
        if( name != null ) {
          map.put(name, chain);
        }
      }
    }
    
    if( def == null ) {
      // construct the default chain
      UpdateRequestProcessorFactory[] factories = new UpdateRequestProcessorFactory[] {
        new RunUpdateProcessorFactory(),
        new LogUpdateProcessorFactory()
      };
      def = new UpdateRequestProcessorChain( factories );
    }
    map.put( null, def );
    map.put( "", def );
    return map;
  }
  

  /**
   * Register the default search components
   */
  private static Map<String, SearchComponent> loadSearchComponents( SolrConfig config )
  {
    Map<String, SearchComponent> components = new HashMap<String, SearchComponent>();
  
    String xpath = "searchComponent";
    NamedListPluginLoader<SearchComponent> loader = new NamedListPluginLoader<SearchComponent>( xpath, components );
    loader.load( config.getResourceLoader(), (NodeList)config.evaluate( xpath, XPathConstants.NODESET ) );
  
    final Map<String,Class<? extends SearchComponent>> standardcomponents 
        = new HashMap<String, Class<? extends SearchComponent>>();
    standardcomponents.put( QueryComponent.COMPONENT_NAME,        QueryComponent.class        );
    standardcomponents.put( FacetComponent.COMPONENT_NAME,        FacetComponent.class        );
    standardcomponents.put( MoreLikeThisComponent.COMPONENT_NAME, MoreLikeThisComponent.class );
    standardcomponents.put( HighlightComponent.COMPONENT_NAME,    HighlightComponent.class    );
    standardcomponents.put( DebugComponent.COMPONENT_NAME,        DebugComponent.class        );
    for( Map.Entry<String, Class<? extends SearchComponent>> entry : standardcomponents.entrySet() ) {
      if( components.get( entry.getKey() ) == null ) {
        try {
          SearchComponent comp = entry.getValue().newInstance();
          comp.init( null ); // default components initialized with nothing
          components.put( entry.getKey(), comp );
        }
        catch (Exception e) {
          SolrConfig.severeErrors.add( e );
          SolrException.logOnce(log,null,e);
        }
      }
    }
    return components;
  }
  

  private IndexDeletionPolicyWrapper initDeletionPolicy() {
    String className = solrConfig.get("mainIndex/deletionPolicy/@class", SolrDeletionPolicy.class.getName());
    IndexDeletionPolicy delPolicy = createInstance(className, IndexDeletionPolicy.class, "Deletion Policy for SOLR");

    Node node = (Node) solrConfig.evaluate("mainIndex/deletionPolicy", XPathConstants.NODE);
    if (node != null) {
      if (delPolicy instanceof NamedListInitializedPlugin)
        ((NamedListInitializedPlugin) delPolicy).init(DOMUtil.childNodesToNamedList(node));
    }
    return new IndexDeletionPolicyWrapper(delPolicy);
  }

  public List<SolrEventListener> parseListener(String path) {
    List<SolrEventListener> lst = new ArrayList<SolrEventListener>();
    log.info( "Searching for listeners: " +path);
    NodeList nodes = (NodeList)solrConfig.evaluate(path, XPathConstants.NODESET);
    if (nodes!=null) {
      for (int i=0; i<nodes.getLength(); i++) {
        Node node = nodes.item(i);
        String className = DOMUtil.getAttr(node,"class");
        SolrEventListener listener = createEventListener(className);
        listener.init(DOMUtil.childNodesToNamedList(node));
        lst.add(listener);
        log.info( "Added SolrEventListener: " + listener);
      }
    }
    return lst;
  }
  

  private void parseListeners() {
    vars.firstSearcherListeners = parseListener("//listener[@event=\"firstSearcher\"]");
    vars.newSearcherListeners = parseListener("//listener[@event=\"newSearcher\"]");
  }
  

  /** Configure the query response writers. There will always be a default writer; additional 
   * writers may also be configured. */
  private void initWriters() {
    String xpath = "queryResponseWriter";
    NodeList nodes = (NodeList) solrConfig.evaluate(xpath, XPathConstants.NODESET);
    
    NamedListPluginLoader<QueryResponseWriter> loader = 
      new NamedListPluginLoader<QueryResponseWriter>( "[solrconfig.xml] "+xpath, vars.responseWriters );
    
    vars.defaultResponseWriter = loader.load( solrConfig.getResourceLoader(), nodes );
    
    // configure the default response writer; this one should never be null
    if (vars.defaultResponseWriter == null) {
      vars.defaultResponseWriter = vars.responseWriters.get("standard");
      if( vars.defaultResponseWriter == null ) {
        vars.defaultResponseWriter = new XMLResponseWriter();
      }
    }

    // make JSON response writers available by default
    if (vars.responseWriters.get("json")==null) {
      vars.responseWriters.put("json", new JSONResponseWriter());
    }
    if (vars.responseWriters.get("python")==null) {
      vars.responseWriters.put("python", new PythonResponseWriter());
    }
    if (vars.responseWriters.get("ruby")==null) {
      vars.responseWriters.put("ruby", new RubyResponseWriter());
    }
    if (vars.responseWriters.get("raw")==null) {
      vars.responseWriters.put("raw", new RawResponseWriter());
    }
    if (vars.responseWriters.get("javabin") == null) {
      vars.responseWriters.put("javabin", new BinaryResponseWriter());
    }
  }
  

  /** Configure the query parsers. */
  private void initQParsers() {
    String xpath = "queryParser";
    NodeList nodes = (NodeList) solrConfig.evaluate(xpath, XPathConstants.NODESET);

    NamedListPluginLoader<QParserPlugin> loader =
      new NamedListPluginLoader<QParserPlugin>( "[solrconfig.xml] "+xpath, vars.qParserPlugins);

    loader.load( solrConfig.getResourceLoader(), nodes );

    // default parsers
    for (int i=0; i<QParserPlugin.standardPlugins.length; i+=2) {
     try {
       String name = (String)QParserPlugin.standardPlugins[i];
       if (null == vars.qParserPlugins.get(name)) {
         Class<QParserPlugin> clazz = (Class<QParserPlugin>)QParserPlugin.standardPlugins[i+1];
         QParserPlugin plugin = clazz.newInstance();
         vars.qParserPlugins.put(name, plugin);
         plugin.init(null);
       }
     } catch (Exception e) {
       throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, e);
     }
    }
  }



  /** Configure the ValueSource (function) plugins */
  private void initValueSourceParsers() {
    String xpath = "valueSourceParser";
    NodeList nodes = (NodeList) solrConfig.evaluate(xpath, XPathConstants.NODESET);

    NamedListPluginLoader<ValueSourceParser> loader =
      new NamedListPluginLoader<ValueSourceParser>( "[solrconfig.xml] "+xpath, vars.valueSourceParsers);

    loader.load( solrConfig.getResourceLoader(), nodes );

    // default value source parsers
    for (Map.Entry<String, ValueSourceParser> entry : ValueSourceParser.standardValueSourceParsers.entrySet()) {
      try {
        String name = entry.getKey();
        if (null == vars.valueSourceParsers.get(name)) {
          ValueSourceParser valueSourceParser = entry.getValue();
          vars.valueSourceParsers.put(name, valueSourceParser);
          valueSourceParser.init(null);
        }
      } catch (Exception e) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, e);
      }
    }
  }
  

  /**
   * Read solrconfig.xml and register the appropriate handlers
   * 
   * This function should <b>only</b> be called from the SolrCore constructor.  It is
   * not intended as a public API.
   * 
   * While the normal runtime registration contract is that handlers MUST be initialized 
   * before they are registered, this function does not do that exactly.
   * 
   * This function registers all handlers first and then calls init() for each one.  
   * 
   * This is OK because this function is only called at startup and there is no chance that
   * a handler could be asked to handle a request before it is initialized.
   * 
   * The advantage to this approach is that handlers can know what path they are registered
   * to and what other handlers are available at startup.
   * 
   * Handlers will be registered and initialized in the order they appear in solrconfig.xml
   */
  private RequestHandlers initHandlersFromConfig( final Config config )  
  {
    final RequestHandlers handlers = new RequestHandlers( vars.infoRegistry );
    AbstractPluginLoader<SolrRequestHandler> loader = 
      new AbstractPluginLoader<SolrRequestHandler>( "[solrconfig.xml] requestHandler", true, true )
    {
      @Override
      protected SolrRequestHandler create( ResourceLoader config, String name, String className, Node node ) throws Exception
      {    
        String startup = DOMUtil.getAttr( node, "startup" );
        if( startup != null ) {
          if( "lazy".equals( startup ) ) {
            log.info("adding lazy requestHandler: " + className );
            NamedList args = DOMUtil.childNodesToNamedList(node);
            log.error( "Can't handler lazy loading yet...." );
            return super.create( config, name, className, node );
            //return new LazyRequestHandlerWrapper( core, className, args );
          }
          else {
            throw new Exception( "Unknown startup value: '"+startup+"' for: "+className );
          }
        }
        return super.create( config, name, className, node );
      }

      @Override
      protected SolrRequestHandler register(String name, SolrRequestHandler plugin) throws Exception {
        return handlers.register( name, plugin );
      }
      
      @Override
      protected void init(SolrRequestHandler plugin, Node node ) throws Exception {
        plugin.init( DOMUtil.childNodesToNamedList(node) );
      }      
    };
    
    NodeList nodes = (NodeList)config.evaluate("requestHandler", XPathConstants.NODESET);
    
    // Load the handlers and get the default one
    SolrRequestHandler defaultHandler = loader.load( config.getResourceLoader(), nodes );
    if( defaultHandler == null ) {
      defaultHandler = handlers.get(RequestHandlers.DEFAULT_HANDLER_NAME);
      if( defaultHandler == null ) {
        defaultHandler = new StandardRequestHandler();
        handlers.register(RequestHandlers.DEFAULT_HANDLER_NAME, defaultHandler);
      }
    }
    handlers.register(null, defaultHandler);
    handlers.register("", defaultHandler);
    return handlers;
  }
    
  

  // FROM UPDATE HANDLER
  private void parseEventListeners() {
    NodeList nodes = (NodeList) solrConfig.evaluate("updateHandler/listener[@event=\"postCommit\"]", XPathConstants.NODESET);
    if (nodes!=null) {
      for (int i=0; i<nodes.getLength(); i++) {
        Node node = nodes.item(i);
        try {
          String className = DOMUtil.getAttr(node,"class");
          SolrEventListener listener = createEventListener(className);
          listener.init(DOMUtil.childNodesToNamedList(node));
          // listener.init(DOMUtil.toMapExcept(node.getAttributes(),"class","synchronized"));
          vars.commitCallbacks.add(listener);
          log.info("added SolrEventListener for postCommit: " + listener);
        } catch (Exception e) {
          throw new SolrException( SolrException.ErrorCode.SERVER_ERROR,"error parsing event listevers", e, false);
        }
      }
    }
    nodes = (NodeList) solrConfig.evaluate("updateHandler/listener[@event=\"postOptimize\"]", XPathConstants.NODESET);
    if (nodes!=null) {
      for (int i=0; i<nodes.getLength(); i++) {
        Node node = nodes.item(i);
        try {
          String className = DOMUtil.getAttr(node,"class");
          SolrEventListener listener = createEventListener(className);
          listener.init(DOMUtil.childNodesToNamedList(node));
          vars.optimizeCallbacks.add(listener);
          log.info("added SolarEventListener for postOptimize: " + listener);
        } catch (Exception e) {
          throw new SolrException( SolrException.ErrorCode.SERVER_ERROR,"error parsing event listeners", e, false);
        }
      }
    }
  }
  
  
  //--------------------------------------------------------------------------
  //--------------------------------------------------------------------------
  

  public SolrConfig getSolrConfig() {
    return solrConfig;
  }


  public CoreDescriptor getCoreDescriptor() {
    return coreDescriptor;
  }
}
