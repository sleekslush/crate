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

package io.crate.analyze;

import io.crate.metadata.table.ColumnPolicy;
import io.crate.planner.symbol.DynamicReference;
import io.crate.planner.symbol.Reference;
import io.crate.planner.symbol.Symbol;
import io.crate.types.DataTypes;
import org.elasticsearch.common.Nullable;
import io.crate.exceptions.ColumnUnknownException;

import java.util.HashMap;
import java.util.Map;

public class NewColumnCollector extends DispatchingReferenceSymbolVisitor.InnerVisitor<NewColumnCollector.NewColumnContext> {

    private Map<Reference, AnalyzedColumnDefinition> columnDefinitions = new HashMap<>();

    public static class NewColumnContext implements DispatchingReferenceSymbolVisitor.InnerVisitorContext {
        public AnalyzedColumnDefinition root = null;
        public Boolean mappingChange = false;
    }

    @Override
    public NewColumnContext createContext() {
        return new NewColumnContext();
    }

    @Override
    public void visit(Reference ref, Symbol symbol, @Nullable Reference parent, NewColumnContext ctx) {

        if (ref instanceof DynamicReference || ref.valueType() == DataTypes.OBJECT) {
            if(parent != null && parent.valueType() == DataTypes.OBJECT){
                if(parent.info().columnPolicy() == ColumnPolicy.IGNORED){
                    return;
                } else if(ref instanceof DynamicReference && parent.info().columnPolicy() == ColumnPolicy.STRICT){
                    throw new ColumnUnknownException(ref.info().ident().columnIdent().name(),
                                                     ref.info().ident().columnIdent().fqn());
                }
            }
            AnalyzedColumnDefinition analyzedParent = columnDefinitions.get(parent);
            AnalyzedColumnDefinition analyzedColumnDefinition = new AnalyzedColumnDefinition(ref, analyzedParent);
            if(analyzedParent != null){
                analyzedParent.addChild(analyzedColumnDefinition);
            }
            if (ctx.root == null) {
                ctx.root = analyzedColumnDefinition;
            }
            if((ref instanceof DynamicReference) &&
                    !(ref.valueType() == DataTypes.OBJECT && ref.info().columnPolicy() == ColumnPolicy.IGNORED)){
                ctx.mappingChange = true;
            }
            columnDefinitions.put(ref, analyzedColumnDefinition);
        }
    }
}