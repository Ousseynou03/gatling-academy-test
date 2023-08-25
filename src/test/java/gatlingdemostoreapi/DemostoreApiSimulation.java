package gatlingdemostoreapi;

import java.util.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class DemostoreApiSimulation extends Simulation {

  private HttpProtocolBuilder httpProtocol = http
          .baseUrl("http://demostore.gatling.io")
          .acceptHeader("application/json");



  private static Map<CharSequence, String> headers_3 = Map.ofEntries(
    Map.entry("Content-Type", "application/json"),
    Map.entry("Origin", "http://demostore.gatling.io")
  );

  private static Map<CharSequence, String> authorizationHeaders = Map.ofEntries(
    Map.entry("Content-Type", "application/json"),
    Map.entry("Origin", "http://demostore.gatling.io"),
    Map.entry("authorization", "Bearer #{jwt}")
  );


  private static ChainBuilder initSession = exec(session -> session.set("authenticated",false));

  private static class Authentication {
    private static ChainBuilder authenticate =
            doIf(session -> !session.getBoolean("authenticated")).then(
    exec(http("Authenticate")
            .post("/api/authenticate")
            .headers(headers_3)
            .body(StringBody("""
                    {
                      "username": "admin",
                      "password": "admin"
                    }
                    """))
            .check(status().is(200))
            .check(jsonPath("$.token").saveAs("jwt")))
            .exec(session -> session.set("authenticated",true))
            );
  }

  private static class Categories {
    private static FeederBuilder.Batchable<String> categoriesFeeder =
            csv("data/categories.csv").random();
    private static ChainBuilder list =
            exec(http("List Categories")
        .get("/api/category")
        .check(jsonPath("$[?(@.id==6)].name").is("For Her"))
    );

    private static ChainBuilder update =
     feed(categoriesFeeder)
    .exec(Authentication.authenticate)
   .exec(Authentication.authenticate)
    .exec(http("Update Categorie")
        .put("/api/category/#{categoryId}")
        .headers(authorizationHeaders)
        .body(StringBody("""
                {
                  "name": "#{categoryName}"
                }
                """))
      // .check(jsonPath("$.name").is("#{categoryId}"))
      .check(jmesPath("name").isEL("#{categoryName}"))
            );
  }

  private static class Products {
    private static FeederBuilder.Batchable<String> productsFeeder =
            csv("data/products.csv").circular();
    private static ChainBuilder list =
            exec(http("List Products")
        .get("/api/product")
        .check(jmesPath("[*].id").ofList().saveAs("allProductIds"))
        .check(status().is(200))
    );

    private static ChainBuilder get =
         exec(session -> {
           List<Integer> allProductIds = session.getList("allProductIds");
           return session.set("productId", allProductIds.get(new Random().nextInt(allProductIds.size())));
         })
      .exec(session -> {
                System.out.println("allProductIds captured :" +session.get("allProductIds").toString());
                System.out.println("productId selected :" +session.get("productId").toString());
                           return session;
                         }
                 )
    .exec(http("Get Product")
        .get("/api/product/34")
    );

    private static ChainBuilder update =
    feed(productsFeeder)
    .exec(Authentication.authenticate)
    .exec(http("Update Product")
        .put("/api/product/34")
        .headers(authorizationHeaders)
        .body(StringBody("""
                {
                  "name": "#{productName}",
                  "description": "#{productDescription}",
                  "image": "#{productImage}",
                  "price": "#{productPrice}",
                  "categoryId": #{productCategoryId}
                }
                """))
            );

    private static ChainBuilder create =
            exec(Authentication.authenticate)
                    .feed(productsFeeder)
                    .exec(http("Create product #{productName}")
                            .post("/api/product")
                            .headers(authorizationHeaders)
                            .body(ElFileBody("data/create-product.json")));

  }


  private ScenarioBuilder scn = scenario("DemostoreApiSimulation")
    .exec(initSession)
    .exec(Categories.list)
    .pause(2)
    .exec(Products.list)
    .pause(2)
    .exec(Products.get)
    .pause(2)
    .exec(Products.update)
    .pause(2)
    .repeat(3).on(exec(Products.create))
    .pause(2)
    .exec(Categories.update);


  {
	  setUp(scn.injectOpen(atOnceUsers(1))).protocols(httpProtocol);
  }
}
