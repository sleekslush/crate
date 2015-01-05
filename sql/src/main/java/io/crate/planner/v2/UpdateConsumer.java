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

package io.crate.planner.v2;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.crate.analyze.AnalysisMetaData;
import io.crate.analyze.UpdateAnalyzedStatement;
import io.crate.analyze.WhereClause;
import io.crate.analyze.relations.AnalyzedRelation;
import io.crate.analyze.relations.PlannedAnalyzedRelation;
import io.crate.analyze.relations.RelationVisitor;
import io.crate.analyze.relations.TableRelation;
import io.crate.analyze.where.WhereClauseAnalyzer;
import io.crate.analyze.where.WhereClauseContext;
import io.crate.metadata.ReferenceIdent;
import io.crate.metadata.ReferenceInfo;
import io.crate.metadata.table.TableInfo;
import io.crate.planner.PlanNodeBuilder;
import io.crate.planner.PlannerContextBuilder;
import io.crate.planner.RowGranularity;
import io.crate.planner.node.dml.UpdateNode;
import io.crate.planner.node.dql.CollectNode;
import io.crate.planner.node.dql.MergeNode;
import io.crate.planner.projection.Projection;
import io.crate.planner.projection.UpdateProjection;
import io.crate.planner.symbol.Reference;
import io.crate.planner.symbol.Symbol;
import io.crate.types.DataTypes;

import java.util.ArrayList;
import java.util.List;

import static io.crate.planner.symbol.Field.unwrap;

public class UpdateConsumer implements Consumer {
    private final Visitor visitor;

    public UpdateConsumer(AnalysisMetaData analysisMetaData) {
        visitor = new Visitor(analysisMetaData);
    }

    @Override
    public boolean consume(AnalyzedRelation rootRelation, ConsumerContext context) {
        PlannedAnalyzedRelation plannedAnalyzedRelation = visitor.process(rootRelation, null);
        if (plannedAnalyzedRelation == null) {
            return false;
        }
        context.rootRelation(plannedAnalyzedRelation);
        return true;
    }

    private static class Visitor extends RelationVisitor<Void, PlannedAnalyzedRelation> {

        private final AnalysisMetaData analysisMetaData;

        public Visitor(AnalysisMetaData analysisMetaData) {
            this.analysisMetaData = analysisMetaData;
        }

        @Override
        public PlannedAnalyzedRelation visitUpdateAnalyzedStatement(UpdateAnalyzedStatement statement, Void context) {
            assert statement.sourceRelation() instanceof TableRelation : "sourceRelation of update statement must be a TableRelation";
            TableRelation tableRelation = (TableRelation) statement.sourceRelation();
            TableInfo tableInfo = tableRelation.tableInfo();

            if (tableInfo.schemaInfo().systemSchema() || tableInfo.rowGranularity() != RowGranularity.DOC) {
                return null;
            }

            List<CollectNode> collectNodes = new ArrayList<>(statement.nestedStatements().size());
            for (UpdateAnalyzedStatement.NestedAnalyzedStatement nestedAnalysis : statement.nestedStatements()) {
                WhereClauseAnalyzer whereClauseAnalyzer = new WhereClauseAnalyzer(analysisMetaData, tableRelation);
                WhereClauseContext whereClauseContext = whereClauseAnalyzer.analyze(nestedAnalysis.whereClause());
                WhereClause whereClause = whereClauseContext.whereClause();

                if (!whereClause.noMatch() || !(tableInfo.isPartitioned() && whereClause.partitions().isEmpty())) {
                    // for updates, we always need to collect the `_uid`
                    ReferenceIdent uidIdent = new ReferenceIdent(tableInfo.ident(), "_uid");
                    Reference uidReference = new Reference(new ReferenceInfo(uidIdent, RowGranularity.DOC, DataTypes.STRING));

                    PlannerContextBuilder contextBuilder = new PlannerContextBuilder()
                            .output(Lists.newArrayList((Symbol)uidReference))
                            .output(unwrap(nestedAnalysis.assignments().values()));

                    UpdateProjection updateProjection = new UpdateProjection(
                            nestedAnalysis.assignments(),
                            contextBuilder.outputs(),
                            whereClause.version().orNull());

                    CollectNode node = PlanNodeBuilder.collect(
                            tableInfo,
                            whereClause,
                            contextBuilder.toCollect(),
                            ImmutableList.<Projection>of(updateProjection)
                    );

                    collectNodes.add(node);
                }
            }

            MergeNode mergeNode = PlanNodeBuilder.localMerge(ImmutableList.<Projection>of(), collectNodes.get(0));

            return new UpdateNode(collectNodes, mergeNode);
        }

        @Override
        protected PlannedAnalyzedRelation visitAnalyzedRelation(AnalyzedRelation relation, Void context) {
            return null;
        }
    }
}
