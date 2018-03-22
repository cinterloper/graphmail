package net.iowntheinter.graphmail

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import net.iowntheinter.kvdn.service.KvdnService
import net.iowntheinter.kvdn.util.KVDNTxEvent
import net.iowntheinter.kvdn.util.KvdnHooks

@TypeChecked
@CompileStatic
class GraphMailQueryEngineVerticle extends AbstractVerticle {


    Logger logger = LoggerFactory.getLogger(this.class.getName())
    MailGraphAccess mga
    KvdnService KVService

    final String defaultconfpath = 'conf/janusgraph-berkeleyje-lucene.properties'

    @Override
    void start(Future<Void> startFuture) throws Exception {
        final JsonObject config = vertx.getOrCreateContext().config()

        logger.info("start of ${this.class.name}")
        mga = new MailGraphAccess("GRAPH_MAIL", vertx)

        assert new File(defaultconfpath).exists()
        logger.info("config: ${config.encodePrettily()}")
        if (config.containsKey("remote_graph_config")) {
            logger.warn("RUNNING IN REMOTE MODE")
            mga.openRemoteGraph(config.getString("remote_graph_config"))
        } else {
            logger.warn("RUNNING IN EMBEDDED MODE")
            mga.openGraph(defaultconfpath) //embeded
        }
        KVService = KvdnService.createProxy(vertx, "kvsvc")

        setupListeners({ ar ->
            logger.info("listeners setup")
        })
        startFuture.complete()
    }

    @Override
    void stop(Future<Void> stopFuture) throws Exception {
        mga.closeGraph()
        stopFuture.complete()
    }

    void updateDegDist(Handler<AsyncResult<JsonObject>> cb) {
        logger.info("going to compute degDist")
        mga.computeVertexDegree({ AsyncResult<JsonObject> result ->
            logger.info("computed degDist")

            JsonObject degDistPayload
            if (result.succeeded()) {
                degDistPayload = result.result()
                KVService.set("GRAPH_METRICS", "degDist", degDistPayload.toString(), new JsonObject(),
                        { AsyncResult<JsonObject> metricsSetRes ->

                            if (metricsSetRes.succeeded()) {
                                logger.info("updated graph degree distribution metrics")
                                cb.handle(Future.succeededFuture(degDistPayload))
                            } else {
                                metricsSetRes.cause().printStackTrace()
                                logger.error(metricsSetRes.cause())
                                cb.handle(Future.failedFuture(metricsSetRes.cause()))
                            }
                        })
            } else {
                result.cause().printStackTrace()
                logger.error(result.cause())
            }

        })
    }

    void setupListeners(Handler<AsyncResult> cb) {
        KvdnHooks eventListener = new KvdnHooks(vertx)
        eventListener.onWrite(mga.straddr, { KVDNTxEvent ktxe ->

            logger.info("got write on $ktxe.key for $mga.straddr")
            getMailJson(ktxe.key, { AsyncResult<Map> ar ->
                if (ar.succeeded()) {
                    logger.info("get $ktxe.key result from svc proxy")
                    mga.processMailJson(ktxe.key, ar.result(), { AsyncResult ->
                        logger.info("ran process mail json for $ktxe.key , will update degDist")
                    })
                } else {
                    logger.fatal(ar.cause())
                }

            })


        })
        vertx.eventBus().consumer("degDist", { Message message ->
            updateDegDist({ AsyncResult<LinkedHashMap> res ->
                if (res.succeeded()) {
                    message.reply(res.result())
                } else {
                    message.reply("ERROR COMPUTING DEGDIST")
                    logger.error(res.cause())
                }
            })
        })

        cb.handle(Future.succeededFuture())
    }

    void getMailJson(String key, Handler<AsyncResult<JsonObject>> cb) {
        KVService.get(mga.straddr, key, new JsonObject(), { AsyncResult<String> valueResult ->
            if (valueResult.failed()) {
                cb.handle(Future.failedFuture(valueResult.cause()))
            } else {
                cb.handle(Future.succeededFuture(new JsonObject(valueResult.result())))
                // why do we get a Map on the other side of this, this must be a weird vertx-groovy bug
                // -- does this still happen now that vertx-lang-groovy is not included?
            }

        })
    }
}
