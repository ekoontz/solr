package org.apache.solr.schema;

import org.apache.lucene.search.Similarity;

public class CustomSimilarityFactory extends SimilarityFactory {
  @Override
  public Similarity getSimilarity() {
    return new MockConfigurableSimilarity(params.get("echo"));
  }
}
