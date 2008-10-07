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

require 'net/http'

#TODO: Look at SolrJ's CommonsHttpSolrServer and borrow anything useful from there, such as followRedirects, compression, retries, etc
class Solr::Server
  attr_reader :url
  
  def initialize(url="http://localhost:8983/solr", options={})
    @url = URI.parse(url)
    unless @url.kind_of? URI::HTTP
      raise "invalid http url: #{url}"
    end
  
    # Not actually opening the connection yet, just setting up the persistent connection.
    @connection = Net::HTTP.new(@url.host, @url.port)
    @connection.read_timeout = options[:timeout] if options[:timeout]
  end
  
  def add(docs)
  end
  
  def commit
  end
  
  def optimize
  end
  
  def delete_by_id(id)
  end
  
  def delete_by_query(query)
  end
  
  def ping
  end
  
  def query(params)
  end
  
  def request(request)
    # TODO: should we allow get and post, as SolrRequest does in Java?
    response = @connection.post(@url.path + "/" + request.handler,
                                request.to_s,
                                { "Content-Type" => request.content_type })
  
    case response
    when Net::HTTPSuccess then response.body
    else
      response.error!
    end
  end
end