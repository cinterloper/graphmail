package net.iowntheinter.graphmail

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LBLogger
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.auth.AuthOptions
import io.vertx.ext.auth.shiro.ShiroAuthOptions
import io.vertx.ext.auth.shiro.ShiroAuthRealmType
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.BridgeOptions
import io.vertx.ext.web.handler.sockjs.PermittedOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.serviceproxy.ServiceBinder
import net.iowntheinter.kvdn.server.impl.HTTPServer
import net.iowntheinter.kvdn.service.impl.KvdnServiceImpl
import net.iowntheinter.kvdn.service.KvdnService

@TypeChecked
@CompileStatic
class GraphMailCoreServer extends AbstractVerticle {

    @Override
    void start(Future<Void> startFuture) throws Exception {

        ((LBLogger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).
                setLevel(Level.INFO)

        Logger logger = new LoggerFactory().getLogger("kvdn")

        Router router = Router.router(vertx)

        //AuthOptions opts = new ShiroAuthOptions().setConfig(config).setType(ShiroAuthRealmType.PROPERTIES)
        //AuthProvider provider = ShiroAuth.create(v, opts);


        Handler<RoutingContext> sjsh = SockJSHandler.create(vertx)
        BridgeOptions options = new BridgeOptions()
                .addOutboundPermitted(new PermittedOptions()
                .setAddressRegex(".*"))
                .addInboundPermitted(new PermittedOptions()
                .setAddressRegex(".*"))
        sjsh.bridge(options)



        router.route("/eb/*").handler(sjsh)
        router.route().handler(BodyHandler.create())
        router.route("/degDist").handler({ RoutingContext rctx ->


            vertx.eventBus().send("degDist", "update",{ Future<Message<LinkedHashMap>> freply ->
                def reply = freply.result()
                HttpServerResponse resp = rctx.response()
                resp.end(new JsonObject(reply.body()).toBuffer())
            })
        })

        KvdnServiceImpl service = new KvdnServiceImpl(vertx)
        HTTPServer s
        service.setup({ AsyncResult r ->
            LoggerFactory.getLogger(this.class.name).debug("setup KvdnServiceImpl complete")
            new ServiceBinder(this.vertx).setAddress("kvsvc").register(KvdnService.class, service)
            s = new HTTPServer(vertx, service)


            s.init(router as Router, { AsyncResult result ->
                router.route().handler(StaticHandler.create())

                router.route().handler(CookieHandler.create())
                router.route().handler(BodyHandler.create())
                router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)))

                router.route("/logout").handler({ RoutingContext context ->
                    context.clearUser()
                    // Redirect back to the index page
                    context.response().putHeader("location", "/").setStatusCode(302).end()
                })

                try {

                    HttpServer server = vertx.createHttpServer()

                    server.requestHandler(router.&accept).listen(6502)
                } catch (e) {
                    logger.error "could not setup http server:" + e.getMessage()
                }


                logger.info("core config: ${vertx.getOrCreateContext().config()}")


                JsonObject mconfig = vertx.getOrCreateContext().config()
                DeploymentOptions opts = new DeploymentOptions().
                        setWorker(true).
                        setConfig(mconfig)
                if (mconfig.containsKey("remote_graph_config")) {
                  //  opts.setInstances(3)

                }
                vertx.deployVerticle(
                        "net.iowntheinter.graphmail.GraphMailQueryEngineVerticle", opts)
                startFuture.complete()
            })

        })
    }


    @Override
    void stop(Future<Void> stopFuture) throws Exception {

        stopFuture.complete()
    }


}
