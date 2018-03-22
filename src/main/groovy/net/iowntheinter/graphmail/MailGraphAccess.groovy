package net.iowntheinter.graphmail

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.process.traversal.Scope
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__
import org.apache.tinkerpop.gremlin.structure.Vertex

@TypeChecked
@CompileStatic
class MailGraphAccess extends GraphAccess {


    class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private int cacheSize

        LRUCache(int cacheSize) {
            super((int) 16, (long) 0.75, true)
            this.cacheSize = cacheSize
        }

        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() >= cacheSize
        }
    }

    public final String straddr
    private final Vertx vertx

    private LRUCache vertexCache

    MailGraphAccess(String straddr, Vertx v) {
        logger = LoggerFactory.getLogger(this.class.name)
        if (!straddr) {
            this.straddr = 'GRAPH_MAIL'
        } else {
            this.straddr = straddr
        }
        this.vertx = v
    }

    class MailMessage {
        String hashKey
        ArrayList<Map<String, String>> froms
        ArrayList<Map<String, String>> tos
        String subject
        String thread
    }

    class identity {
        String name
        ArrayList<String> emails
    }

    void processMailJson(String hashKey, Map mailObject, Handler<AsyncResult> cb) {

        MailMessage m = new MailMessage()
        m.hashKey = hashKey
        m.froms = mailObject.from as ArrayList
        m.tos = mailObject.to as ArrayList

        logger.info("PROCESSING $hashKey")
        logger.info mailObject.from
        logger.info mailObject.to

        processMailIntoGraph(hashKey, m, cb)

    }


    void processMailIntoGraph(String mailHashKey, MailMessage mail, Handler<AsyncResult> cb) {
        createElement(mailHashKey, mail)
        cb.handle(Future.succeededFuture())

    }

    void computeVertexDegree(Handler<AsyncResult<JsonObject>> cb) {
        logger.info('computing degree distribution')
        JsonObject result = new JsonObject()
        ArrayList<Integer> X = new ArrayList<>()
        ArrayList<Integer> Y = new ArrayList<>()
        boolean success = false
        try {
            def m = g.V().groupCount().by(__.both().count()).order(Scope.local).by(Order.keyIncr).next()
            m.each { k, v -> X.add(k as Integer); Y.add(v as Integer) }
            result.put("X", new JsonArray(X)).put("Y", new JsonArray(Y))
            success = true
        } catch (e) {
            success = false
            cb.handle(Future.failedFuture(e))
        }
        if (success && (result != null))
            try {
                cb.handle(Future.succeededFuture(result))
            }catch(e){
                e.printStackTrace()
            }
        else
            cb.handle(Future.failedFuture("got null result"))
    }

    void createElement(String mailHashKey, MailMessage mail) {
        try {
            // naive check if the Vertex was previously created
            if (g.V().has("hashKey", mailHashKey).hasNext()) {
                Map foundv = g.V().has("hashKey", mailHashKey).propertyMap().next()
                logger.warn("already processed this message?: $mailHashKey ")
                logger.warn(foundv)
                if (supportsTransactions) {
                    g.tx().rollback()
                }
                return
            }



            final Vertex message = g.addV("MESSAGE").property("hashKey", mailHashKey).next()
            logger.info("ADDED: " + message.id())
//            if (!sourceEmailExists)
//                message = g.addV(mail.froms[0].email).property('mailHashKey', mailHashKey).next()
//            else
//                message = g.V(mail.froms[0].email).next()
            mail.froms.each { id ->
                id.email = id.email.trim().toLowerCase()
                boolean sourceEmailExists = g.V().has('email_id', id.email).hasNext()
                g.V(message).property('fromEmail', id.email).next()
                g.V(message).property('fromName', id.name).next()
                if (sourceEmailExists) {
                    final fromV = g.V().has('email_id', id.email).next()
                    g.V(fromV).as('source').V(message).addE("RECIEVED_FROM").to('source').next()
                } else {
                    //create the email idenitity to vertex
                    final Vertex fromV = g.addV("EMAIL_IDENTITY").property("email_id", id.email).next()
                    g.V(fromV).as('source').V(message).addE("RECIEVED_FROM").to('source').next()

                }

            }
            mail.tos.each { Map<String, String> id ->
                id.email = id.email.trim().toLowerCase()
                g.V(message).property('toEmail', id.email).next()
                g.V(message).property('toName', id.name).next()
                boolean destEmailExists = g.V().has('email_id', id.email).hasNext()
                if (destEmailExists) {
                    final toV = g.V().has('email_id', id.email).next()
                    g.V(toV).as("dest").V(message).addE("SENT_TO").from("dest").next()
                } else {
                    //create the to vertex
                    final Vertex toV = g.addV("EMAIL_IDENTITY").property("email_id", id.email).next()
                    g.V(toV).as("dest").V(message).addE("SENT_TO").from("dest").next()

                }
            }



            logger.info("creating elements")

            if (supportsTransactions) {
                g.tx().commit()
            }

        } catch (Exception e) {
            logger.error(e.getMessage(), e)
            if (supportsTransactions) {
                g.tx().rollback()
            }
        }
    }
}
