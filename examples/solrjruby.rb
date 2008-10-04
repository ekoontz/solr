# This example demonstrates basic SolrJ API searching usage from JRuby
# 
# Requirements:
#
#   * JRuby (1.1.4 was used)
#   * Solr (1.3.0 Final Release)
#
# To run, adjust the solr_dist_root and solr_home paths:
#
#      jruby solrjruby.rb
#
# This example can run either Solr in embedded mode, or access Solr over HTTP.

require 'java'

solr_dist_root = "/Users/erik/apache-solr-1.3.0"
solr_home = "/Users/erik/apache-solr-1.3.0/example/solr"

def require_jars(dir)
  jar_pattern = File.join(dir,"**", "*.jar")
  jar_files = Dir.glob(jar_pattern)

  jar_files.each {|jar_file| require jar_file}
end

require_jars(File.join(solr_dist_root, "lib"))
require_jars(File.join(solr_dist_root, "dist"))

import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.core.CoreContainer
import org.apache.solr.core.CoreDescriptor
import org.apache.solr.client.solrj.SolrQuery

container = CoreContainer.new
descriptor = CoreDescriptor.new(container, "core1", solr_home)
core = container.create(descriptor)
container.register("core1", core, false)


solr = EmbeddedSolrServer.new(container, "core1")
#solr = CommonsHttpSolrServer.new("http://localhost:8983/solr")
query = SolrQuery.new("*:*")
response = solr.query(query)

puts response

core.close
