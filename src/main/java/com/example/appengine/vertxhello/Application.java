

package com.example.appengine.vertxhello;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Application extends AbstractVerticle {

    static String METADATA_HOST = "metadata.google.internal";
    static int METADATA_PORT = 80;
    WebClient webClient;
    private static final Logger logger = Logger.getLogger(Application.class.getName());

    @Override
    public void start(Future<Void> startFuture) {
        webClient = WebClient.create(vertx);
        Router router = Router.router(vertx);

        logger.severe("Vertx server initializing");

        JsonObject postgreSQLClientConfig = new JsonObject();
        postgreSQLClientConfig.put("host" ,"34.77.113.51");
        postgreSQLClientConfig.put("port" ,5432);
        postgreSQLClientConfig.put("database" ,"myapp");
        postgreSQLClientConfig.put("username" ,"postgres");
        postgreSQLClientConfig.put("password" ,"qwerty1@");

        // Init connection pool - open and close client
        SQLClient client = PostgreSQLClient.createShared(vertx ,postgreSQLClientConfig);
        client.getConnection(res->{if (res.failed()) {
            logger.severe("fail to connect!" + res.toString());
        } else {
            res.result().close(k->{});
        }
        });

        // Does't work in as expected in google cloud
        //router.route().handler(BodyHandler.create());

        router.route("/hash*").handler(new Handler<RoutingContext>() {
                    @Override
                    public void handle(RoutingContext routingContext) {
                        client.getConnection(new Handler<AsyncResult<SQLConnection>>() {
                            @Override
                            public void handle(AsyncResult<SQLConnection> res) {
                                if (res.failed()) {
                                    logger.severe("fail to connect!" + res.toString());
                                    routingContext.fail(res.cause());
                                } else {
                                    SQLConnection conn = res.result();

                                    // save the connection on the context
                                    routingContext.put("conn" ,conn);

                                    // we need to return the connection back to the jdbc pool. In order to do that we need to close it, to keep
                                    // the remaining code readable one can add a headers end handler to close the connection.
                                    routingContext.addHeadersEndHandler(done -> conn.close(v -> {
                                    }));


                                    routingContext.next();
                                }
                            }
                        });

                    }
        }).failureHandler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                logger.severe("handle failed!");
                SQLConnection conn = routingContext.get("conn");
                if (conn != null) {
                    conn.close(new Handler<AsyncResult<Void>>() {
                        @Override
                        public void handle(AsyncResult<Void> v) {
                        }
                    });
                }
            }
        });

        router.get("/test").handler(routingContext -> {
            routingContext.response().putHeader("content-type" ,"text/html").end("Hello World!");
        });

        router.post("/hashadd").handler(this::handleAddHash);

        router.get("/hashget/:hashID").handler(this::handleGetHash);

        router.route().handler(routingContext -> {

            String posthtml = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<body>\n" +
                    "<form action=\"/hashadd\" method=\"post\">" +
                    "<label for=\"url\">Url</label><br>" +
                    "  <input id=\"url\" name=\"url\" value=\"yandex.ru\">\n" +
                    "  <input type=\"submit\" value=\"Submit\">\n" +
                    "</form>\n" +
                    "</body>\n" +
                    "</html>";

            logger.info("Logging INFO with java.util.logging");
            logger.severe("Logging ERROR with java.util.logging");
            routingContext.response().putHeader("content-type" ,"text/html").end(posthtml);
        });

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT" ,"80"));
        vertx.createHttpServer().requestHandler(router).listen(port ,new Handler<AsyncResult<HttpServer>>() {
            @Override
            public void handle(AsyncResult<HttpServer> ar) {
                startFuture.handle(ar.mapEmpty());
            }
        });
    }


    private static Map<String, String> decodeQueryString(String query) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            for (String param : query.split("&")) {
                String[] keyValue = param.split("=" ,2);
                String key = URLDecoder.decode(keyValue[0] ,"UTF-8");
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1] ,"UTF-8") : "";
                if (!key.isEmpty()) {
                    params.put(key ,value);
                }
            }
            return params;
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // Cannot happen with UTF-8 encoding.
        }
    }

    private void sendError(int statusCode ,HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }

    private void handleAddHash(RoutingContext routingContext) {

        routingContext.request().bodyHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer bodyHandler) {
                        String url = decodeQueryString(bodyHandler.toString()).get("url");
                        logger.info("Logging INFO with java.util.logging");
                        logger.severe("Logging ERROR with java.util.logging");
                        //routingContext.response().putHeader("content-type" ,"text/html").end("Use url: " + url);
                        routingContext.put("url", url);

                        logger.severe("Body is " + routingContext.get("url"));

                        SQLConnection conn = routingContext.get("conn");
                        logger.severe("conn is " + conn.toString());

                        String hash = RandomStringUtils.randomAlphanumeric(9).toUpperCase();

                        if ((!url.startsWith("http://") || !url.startsWith("https://"))) {
                            url = "http://" + url;
                        }

                        conn.updateWithParams("INSERT INTO urlhash (url, hash) VALUES (?, ?)",
                                new JsonArray().add(url).add(hash), query -> {
                            if (query.failed()) {
                                logger.severe("Insert query was failed");
                                sendError(500, routingContext.response());
                            } else {
                                //routingContext.response()
                                routingContext.response().putHeader("content-type", "text/html").end("Use url: "+ "http://nifty-memory-268407.appspot.com/hashget/" + hash);
                            }
                        });
                    }});


    }

    private void handleGetHash(RoutingContext routingContext) {
        String hashID = routingContext.request().getParam("hashID");
        HttpServerResponse response = routingContext.response();
        if (hashID == null) {
          sendError(400, response);
        } else {
          SQLConnection conn = routingContext.get("conn");

          conn.queryWithParams("SELECT id, hash, url FROM urlhash where hash = ?", new JsonArray().add(hashID), query -> {
            if (query.failed()) {
              sendError(500, response);
            } else {
              if (query.result().getNumRows() == 0) {
                sendError(404, response);
              } else {
                logger.severe("Hashget url is: " + query.result().getRows().get(0).getString("url"));
                String url = query.result().getRows().get(0).getString("url");
                response.putHeader("location", url).setStatusCode(302).end();
              }
            }
          });
        }
    }

}
