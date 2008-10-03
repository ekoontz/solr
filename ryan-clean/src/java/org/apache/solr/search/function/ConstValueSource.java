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

package org.apache.solr.search.function;

import org.apache.lucene.index.IndexReader;

import java.io.IOException;

/**
 * <code>ConstValueSource</code> returns a constant for all documents
 */
public class ConstValueSource extends ValueSource {
  final float constant;

  public ConstValueSource(float constant) {
    this.constant = constant;
  }

  @Override
  public String description() {
    return "const(" + constant + ")";
  }

  @Override
  public DocValues getValues(IndexReader reader) throws IOException {
    return new DocValues() {
      @Override
      public float floatVal(int doc) {
        return constant;
      }
      @Override
      public int intVal(int doc) {
        return (int)floatVal(doc);
      }
      @Override
      public long longVal(int doc) {
        return (long)floatVal(doc);
      }
      @Override
      public double doubleVal(int doc) {
        return floatVal(doc);
      }
      @Override
      public String strVal(int doc) {
        return Float.toString(floatVal(doc));
      }
      @Override
      public String toString(int doc) {
        return description();
      }
    };
  }

  @Override
  public int hashCode() {
    return Float.floatToIntBits(constant) * 31;
  }

  @Override
  public boolean equals(Object o) {
    if (ConstValueSource.class != o.getClass()) return false;
    ConstValueSource other = (ConstValueSource)o;
    return  this.constant == other.constant;
  }
}
