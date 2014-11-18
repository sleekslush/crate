/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.operation.projectors.groupby;

import org.apache.lucene.util.BytesRef;
import org.github.jamm.MemoryMeter;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class BytesRefSizeEstimatorTest {

    @Test
    public void testEstimateSize() throws Exception {
        BytesRefSizeEstimator estimator = new BytesRefSizeEstimator();
        long size = estimator.estimateSize(new BytesRef("asdf"));

        MemoryMeter memoryMeter = new MemoryMeter();
        long realSize = memoryMeter.measure(new BytesRef("asdf"));

        assertThat(size, is(realSize));
    }
}