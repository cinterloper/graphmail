// Copyright 2017 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.iowntheinter.graphmail

import io.vertx.core.logging.Logger
import org.apache.commons.configuration.Configuration
import org.apache.commons.configuration.ConfigurationException
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.tinkerpop.gremlin.driver.Client
import org.apache.tinkerpop.gremlin.driver.Cluster
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph
import org.janusgraph.core.JanusGraphFactory
import org.slf4j.LoggerFactory


abstract class GraphAccess {

    protected Logger logger
    protected Configuration conf
    protected Graph graph
    protected GraphTraversalSource g
    protected boolean supportsTransactions


    GraphTraversalSource openRemoteGraph(String propFileName) throws ConfigurationException {
        logger.info("opening graph")
        conf = new PropertiesConfiguration(propFileName)

        Cluster cluster
        Client client
        // using the remote driver for schema
        try {
            cluster = Cluster.open(conf.getString("gremlin.remote.driver.clusterFile"))
            client = cluster.connect()
        } catch (Exception e) {
            throw new ConfigurationException(e)
        }

        graph = EmptyGraph.instance()
        g = graph.traversal().withRemote(conf)
        this.supportsTransactions = false
        return g
    }

    GraphTraversalSource openGraph(String propFileName) throws ConfigurationException {
        logger.info("opening graph")

        conf = new PropertiesConfiguration(propFileName)

        logger.info(conf)

        try {
            graph = JanusGraphFactory.open(conf)
        } catch (Exception e) {
            logger.error(e)
            e.printStackTrace()
        }

        // using the remote graph for queries
        g = graph.traversal()
        this.supportsTransactions = true
        return g
    }


    void closeGraph() throws Exception {
        logger.info("closing graph")
        try {
            if (g != null) {
                g.close()
            }
            if (graph != null) {
                graph.close()
            }
        } finally {
            g = null
            graph = null
        }
    }


}