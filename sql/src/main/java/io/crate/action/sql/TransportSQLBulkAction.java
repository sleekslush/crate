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

package io.crate.action.sql;

import io.crate.analyze.Analysis;
import io.crate.analyze.Analyzer;
import io.crate.executor.Executor;
import io.crate.executor.RowCountResult;
import io.crate.executor.TaskResult;
import io.crate.executor.transport.ResponseForwarder;
import io.crate.operation.collect.StatsTables;
import io.crate.planner.Planner;
import io.crate.sql.tree.Statement;
import io.crate.types.DataType;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.BaseTransportRequestHandler;
import org.elasticsearch.transport.TransportChannel;
import org.elasticsearch.transport.TransportService;

import javax.annotation.Nullable;
import java.util.List;

public class TransportSQLBulkAction extends TransportBaseSQLAction<SQLBulkRequest, SQLBulkResponse> {

    @Inject
    public TransportSQLBulkAction(ClusterService clusterService,
                                  Settings settings,
                                  ThreadPool threadPool,
                                  Analyzer analyzer,
                                  Planner planner,
                                  Provider<Executor> executor,
                                  TransportService transportService,
                                  StatsTables statsTables,
                                  ActionFilters actionFilters) {
        super(clusterService, settings, SQLBulkAction.NAME, threadPool, analyzer,
                planner, executor, statsTables, actionFilters);
        transportService.registerHandler(SQLBulkAction.NAME, new TransportHandler());
    }

    @Override
    public Analysis getAnalysis(Statement statement, SQLBulkRequest request) {
        return analyzer.analyze(statement, SQLRequest.EMPTY_ARGS, request.bulkArgs());
    }

    @Override
    protected SQLBulkResponse emptyResponse(SQLBulkRequest request, String[] outputNames, @Nullable DataType[] types) {
        return new SQLBulkResponse(
                outputNames,
                SQLBulkResponse.EMPTY_RESULTS,
                request.creationTime(),
                types,
                request.includeTypesOnResponse());
    }

    @Override
    protected SQLBulkResponse createResponseFromResult(String[] outputNames,
                                                       DataType[] dataTypes,
                                                       List<TaskResult> result,
                                                       boolean expectsAffectedRows,
                                                       long requestCreationTime,
                                                       boolean includeTypesOnResponse) {
        assert expectsAffectedRows : "bulk operations only works with statements that return rowcounts";
        SQLBulkResponse.Result[] results = new SQLBulkResponse.Result[result.size()];
        for (int i = 0, resultSize = result.size(); i < resultSize; i++) {
            TaskResult taskResult = result.get(i);
            assert taskResult instanceof RowCountResult : "Query operation not supported with bulk requests";
            results[i] = new SQLBulkResponse.Result(taskResult.errorMessage(), (Long) taskResult.rows()[0][0]);
        }
        return new SQLBulkResponse(outputNames, results, requestCreationTime, dataTypes, includeTypesOnResponse);
    }

    private class TransportHandler extends BaseTransportRequestHandler<SQLBulkRequest> {

        @Override
        public SQLBulkRequest newInstance() {
            return new SQLBulkRequest();
        }

        @Override
        public void messageReceived(SQLBulkRequest request, final TransportChannel channel) throws Exception {
            // no need for a threaded listener
            request.listenerThreaded(false);
            ActionListener<SQLBulkResponse> listener = ResponseForwarder.forwardTo(channel);
            execute(request, listener);
        }

        @Override
        public String executor() {
            return ThreadPool.Names.SAME;
        }
    }
}
