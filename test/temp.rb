# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'solr'
require 'erb'

class Request
  def initialize(params)
    @params = params
  end
  
  def handler
    'select'
  end
  
  def content_type
    'application/x-www-form-urlencoded; charset=utf-8'
  end
  
  def to_s
    http_params = []
    @params.each do |key,value|
      if value.respond_to? :each
        value.each { |v| http_params << "#{key}=#{ERB::Util::url_encode(v)}" unless v.nil?}
      else
        http_params << "#{key}=#{ERB::Util::url_encode(value)}" unless value.nil?
      end
    end

    http_params.join("&")
  end
end

solr = Solr::Server.new
response = eval(solr.request(Request.new(:qt => 'standard', :q => '*:*', 'facet.field' => ['cat'], :wt=>'ruby')))
puts response['response']['numFound']

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
import org.apache.solr.common.params.MapSolrParams

solr = CommonsHttpSolrServer.new("http://localhost:8983/solr")
query = {'qt' => 'standard', 'q'=>'*:*', 'facet.field' => 'cat'}
response = solr.query(MapSolrParams.new(query))

puts response.response.get('response').numFound
