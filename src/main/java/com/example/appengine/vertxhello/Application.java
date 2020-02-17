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

import java.util.List;
import java.util.logging.Logger;

//import io.vertx.core.logging.Logger;
//import io.vertx.core.logging.LoggerFactory;

public class Application extends AbstractVerticle {
  //private final Logger LOGGER = LoggerFactory.getLogger( Application.class );
  private static final Logger logger = Logger.getLogger(Application.class.getName());
  private SQLClient client;


  static String METADATA_HOST = "metadata.google.internal";
  static int METADATA_PORT = 80;
  WebClient webClient;

  @Override
  public void start(Future<Void> startFuture) {
    //webClient = WebClient.create(vertx);
    Router router = Router.router(vertx);
    logger.severe("teststring2");

    JsonObject postgreSQLClientConfig = new JsonObject().put("host", "34.77.113.51");
    //JsonObject postgreSQLClientConfig = new JsonObject().put("host", "127.0.0.1");
    postgreSQLClientConfig.put("port",5432);
    postgreSQLClientConfig.put("database","myapp");
    postgreSQLClientConfig.put("username","postgres");
    postgreSQLClientConfig.put("password","qwerty1@");
    SQLClient client = PostgreSQLClient.createShared(vertx, postgreSQLClientConfig);

    router.route("/hash*").handler(routingContext -> client.getConnection(res -> {
      if (res.failed()) {
        routingContext.fail(res.cause());
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
      SQLConnection conn = routingContext.get("conn");
      if (conn != null) {
        conn.close(v -> {
        });
      }
    });

//    postgreSQLClient.getConnection(res -> {
//      if (res.succeeded()) {
//        SQLConnection connection = res.result();
//        connection.query("SELECT * from urlhash", resq -> {
//          if (resq.succeeded()) {
//            // Get the result set
//            ResultSet resultSet = resq.result();
//
//            List<String> columnNames = resultSet.getColumnNames();
//            List<JsonArray> results = resultSet.getResults();
//
//            for (JsonArray row : results) {
//
//              String id = row.getString(0);
//              String hash = row.getString(1);
//              String url = row.getString(2);
//              logger.severe("id: " + id + "hash: " + hash + "url: " + url);
//            }
//          } else {
//            logger.severe("Cannot perform SQL req");
//          }
//        });
//        // Got a connection
//
//      } else {
//        logger.severe("Cannot get SQL connection");
//        // Failed to get connection - deal with it
//      }
//    });

    // Get the PORT environment variable for the server object to listen on
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "80"));


    router.get("/hashget/:hashID").handler(this::handleGetHash);
    //router.get("/").handler(this::handleGetHash);
    //router.post("/products").handler(that::handleAddProduct);
    router.route("/test").handler(routingContext -> {
      //LOGGER.error("teststring");
      logger.info("Logging INFO with java.util.logging");
      logger.severe("Logging ERROR with java.util.logging");
      routingContext.response().putHeader("content-type", "text/html").end("Hello World!");
    });

    router.route("/").handler(routingContext -> {
      //LOGGER.error("teststring");
      logger.info("Logging INFO with java.util.logging");
      logger.severe("Logging ERROR with java.util.logging");
      routingContext.response().putHeader("content-type", "text/html").end("Hello World!");
    });
//    router.route().handler(this::handleDefault);

    vertx.createHttpServer().requestHandler(router).listen(port, ar -> startFuture.handle(ar.mapEmpty()));
  }

  private void sendError(int statusCode, HttpServerResponse response) {
    response.setStatusCode(statusCode).end();
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
