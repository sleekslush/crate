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

package io.crate.planner.node.ddl;


import io.crate.planner.node.PlanVisitor;
import org.elasticsearch.cluster.ClusterService;

import java.util.Map;

public class MappingUpdateNode extends DDLPlanNode {

    private final String[] indices;
    private final boolean partition;
    private final Map<String, Object> mapping;
    private final ClusterService clusterService;

    public MappingUpdateNode(String[] indices,
                             boolean partition,
                             Map<String, Object> mapping,
                             ClusterService clusterService){
        this.indices = indices;
        this.partition = partition;
        this.mapping = mapping;
        this.clusterService = clusterService;
    }

    @Override
    public <C, R> R accept(PlanVisitor<C, R> visitor, C context) {
        return visitor.visitMappingUpdateNode(this, context);
    }

    public String[] indices(){
        return indices;
    }

    public Map<String, Object> mapping() {
        return mapping;
    }

    public ClusterService clusterService() {
        return clusterService;
    }
}