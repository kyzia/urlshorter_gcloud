/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.appengine.vertxhello;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
//import io.vertx.core.json.JsonObject;
//import io.vertx.ext.asyncsql.PostgreSQLClient;
//import io.vertx.ext.sql.SQLClient;
//import io.vertx.ext.sql.SQLConnection;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.List;
import java.util.logging.Logger;

//import io.vertx.core.logging.Logger;
//import io.vertx.core.logging.LoggerFactory;

public class Application extends AbstractVerticle {
  //private final Logger LOGGER = LoggerFactory.getLogger( Application.class );
  private static final Logger logger = Logger.getLogger(Application.class.getName());
  private SQLClient client;


  private static final String CLOUD_SQL_INSTANCE_NAME = System.getenv("DB_INSTANCE");
  private static final String DB_USER = System.getenv("DB_USER");
  private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
  private static final String DB_NAME = System.getenv("DB_DATABASE");
  private static final String DB_UNIX_DOMAIN_SOCKET = CLOUD_SQL_INSTANCE_NAME;
  private static final int DB_PORT = 5432;


  static String METADATA_HOST = "metadata.google.internal";
  static int METADATA_PORT = 8080;
  WebClient webClient;

  @Override
  public void start(Future<Void> startFuture) {
    //webClient = WebClient.create(vertx);
    Router router = Router.router(vertx);
    logger.severe("teststring3");

    JsonObject postgreSQLClientConfig = new JsonObject();
    //postgreSQLClientConfig.put("host", "10.99.160.3");
    postgreSQLClientConfig.put("host", "34.77.113.51");
    //postgreSQLClientConfig.put("host", "nifty-memory-268407:europe-west1:mytestex1");
    //postgreSQLClientConfig.put("host", "/cloudsql/nifty-memory-268407:europe-west1:mytestex1/.s.PGSQL.5432");
    postgreSQLClientConfig.put("port", 5432);
    postgreSQLClientConfig.put("database", "myapp");
    postgreSQLClientConfig.put("username", "postgres");
    postgreSQLClientConfig.put("password", "qwerty1@");

    SQLClient client = PostgreSQLClient.createShared(vertx, postgreSQLClientConfig);

 // This body handler will be called for all routes
    router.route().handler(BodyHandler.create());
    router.route("/hash*").handler(routingContext -> client.getConnection(res -> {
      logger.severe("router route called add called");
      if (res.failed()) {
        logger.severe("Cannot connect to sql - connection failed" + res.toString());
        routingContext.fail(res.cause());
        sendError(400, routingContext.response());
      } else {
        SQLConnection conn = res.result();

        // save the connection on the context
        routingContext.put("conn", conn);

        // we need to return the connection back to the jdbc pool. In order to do that we need to close it, to keep
        // the remaining code readable one can add a headers end handler to close the connection.
        routingContext.addHeadersEndHandler(done -> conn.close(v -> { }));

        routingContext.next();
      }
    })).failureHandler(routingContext -> {
      logger.severe("router route called add called");
      SQLConnection conn = routingContext.get("conn");
      if (conn != null) {
        conn.close(v -> {
        });
      }
    });

    router.post("/hashposttest").handler(this::handleTestProduct);
    router.get("/hashget/:hashID").handler(this::handleGetHash);
    //router.get("/").handler(this::handleGetHash);
    router.post("/hashpost").handler(this::handleAddProduct);
    router.get("/test").handler(routingContext -> {
      //LOGGER.error("teststring");
      logger.info("Logging INFO with java.util.logging");
      logger.severe("Logging ERROR with java.util.logging");
      routingContext.response().putHeader("content-type", "text/html").end("Hello World!");
    });

    router.get("/").handler(routingContext -> {

      String posthtml = "<!DOCTYPE html>\n" +
              "<html>\n" +
              "<body>\n" +
              //"<form action=\"/hashpost\" method=\"POST\" enctype=\"multipart/form-data\">\n" +
              //"<form action=\"/hashpost\" method=\"POST\" enctype=\"application/x-www-form-urlencoded\">\n" +
              "<form action=\"/hashpost\" method=\"post\">" +
              "<label for=\"url\">Url</label><br>" +
              "  <input id=\"url\" name=\"url\" value=\"yandex.ru\">\n" +
              "  <input type=\"submit\" value=\"Submit\">\n" +
              "</form>\n" +
              "</body>\n" +
              "</html>";

      logger.info("Logging INFO with java.util.logging");
      logger.severe("Logging ERROR with java.util.logging");
      routingContext.response().putHeader("content-type", "text/html").end(posthtml);
    });
//    router.route().handler(this::handleDefault);
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "80"));
    vertx.createHttpServer().requestHandler(router).listen(port, ar -> startFuture.handle(ar.mapEmpty()));
  }

  private void sendError(int statusCode, HttpServerResponse response) {
    response.setStatusCode(statusCode).end();
  }

  private void handleTestProduct(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();

    String url = routingContext.request().getFormAttribute("url");
    if ((!url.startsWith("http://") || !url.startsWith("https://"))) {
      url = "http://" + url;
    };
    logger.severe("url: " + url);
    response.putHeader("content-type", "text/html").end("Use url: "+ "http://nifty-memory-268407.appspot.com/hashget/");
  }

  private void handleAddProduct(RoutingContext routingContext) {
    logger.severe("handle add called");
    HttpServerResponse response = routingContext.response();

    SQLConnection conn = routingContext.get("conn");
    String url = routingContext.request().getFormAttribute("url");
    if ((!url.startsWith("http://") || !url.startsWith("https://"))) {
      url = "http://" + url;
    };
    logger.severe("url: " + url);
    String hash = RandomStringUtils.randomAlphanumeric(9).toUpperCase();
    //logger.severe("post data" + url + "hash " + hash);

    conn.updateWithParams("INSERT INTO urlhash (url, hash) VALUES (?, ?)",
            new JsonArray().add(url).add(hash), query -> {
              if (query.failed()) {
                sendError(500, response);
              } else {
                //routingContext.response()
                response.putHeader("content-type", "text/html").end("Use url: "+ "http://nifty-memory-268407.appspot.com/hashget/" + hash);
              }
            });

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
            logger.severe("url is" + query.result().getRows().get(0).getString("url"));
            String url = query.result().getRows().get(0).getString("url");
            response.putHeader("location", url).setStatusCode(302).end();
            //response.putHeader("content-type", "application/json").end(query.result().getRows().get(0).encode());
          }
        }
      });
    }
  }

  private void handleDefault(RoutingContext routingContext) {
    logger.severe("DEFAULT handle accured");
    webClient
        .get(METADATA_PORT, METADATA_HOST, "/computeMetadata/v1/project/project-id")
        .putHeader("Metadata-Flavor", "Google")
        .expect(ResponsePredicate.SC_OK)
        .send(
            res -> {
              if (res.succeeded()) {
                HttpResponse<Buffer> response = res.result();
                routingContext
                    .response()
                    .putHeader("content-type", "text/html")
                    .end("Hello World! from " + response.body());
              } else {
                routingContext.fail(res.cause());
              }
            });
  }
}
