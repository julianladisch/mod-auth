/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.auth.permissions_module_test;

import org.folio.auth.permissions_module.impl.MongoPermissionsStore;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author kurt
 */
@RunWith(VertxUnitRunner.class)
public class MongoPermissionsStoreTest {
  private MongoPermissionsStore store;
  private MongoClient mongoClient;
  private Vertx vertx;
  //private static MongodProcess MONGO;
  //private static int MONGO_PORT = 12345;
  private static String tenant = "diku";
  
  @Rule
  public RunTestOnContext rule = new RunTestOnContext();
  
  @BeforeClass()
  public static void initialize(TestContext context) throws IOException {
    final Async async = context.async();
    async.complete();    
  }

  @AfterClass
  public static void shutDown() {
    //MONGO.stop();
  }
  
  @Before
  public void setUp(TestContext context) throws IOException {
    final Async async = context.async();
    
    int mongoPort = Network.getFreeServerPort();
    MongodStarter starter = MongodStarter.getDefaultInstance();
    IMongodConfig mongodConfig = new MongodConfigBuilder()
            .version(Version.Main.PRODUCTION)
            .net(new Net(mongoPort, Network.localhostIsIPv6()))
            .build();
    MongodExecutable mongodExecutable = starter.prepare(mongodConfig);
    MongodProcess mongoD = mongodExecutable.start();
    
    JsonObject mongoConfig = new JsonObject();
    String host = "localhost";
    mongoConfig.put("connection_string", "mongodb://localhost:" + mongoPort);
    mongoConfig.put("db_name", "test_db");
    vertx = rule.vertx();
    mongoClient = MongoClient.createShared(vertx, mongoConfig);
    
    store = new MongoPermissionsStore(mongoClient);
    ArrayList<JsonObject> permissionList = new ArrayList<>();
    ArrayList<JsonObject> userList = new ArrayList<>();
    permissionList.add(new JsonObject()
            .put("permission_name", "foo.secret")
            .put("tenant", "diku")
            .put("sub_permissions", new JsonArray()));
    permissionList.add(new JsonObject()
            .put("permission_name", "bar.secret")
            .put("tenant", "diku")
            .put("sub_permissions", new JsonArray()));
    permissionList.add(new JsonObject()
            .put("permission_name", "blip.secret")
            .put("tenant", "diku")
            .put("sub_permissions", new JsonArray()));
    permissionList.add(new JsonObject()
            .put("permission_name", "bloop.secret")
            .put("tenant", "diku")
            .put("sub_permissions", new JsonArray()));
    permissionList.add(new JsonObject()
            .put("permission_name", "flop.secret")
            .put("tenant", "diku")
            .put("sub_permissions", new JsonArray()));
    permissionList.add(new JsonObject()
            .put("permission_name", "foobar")
            .put("tenant", "diku")
            .put("sub_permissions", new JsonArray()
              .add("foo.secret")
              .add("bar.secret")));
    permissionList.add(new JsonObject()
        .put("permission_name", "blipbloop")
        .put("tenant", "diku")
        .put("sub_permissions", new JsonArray()
          .add("blip.secret")
          .add("bloop.secret")));
    permissionList.add(new JsonObject()
            .put("permission_name", "master")
            .put("tenant", "diku")
            .put("sub_permissions", new JsonArray()
              .add("foobar")
              .add("blipbloop")
              .add("flop.secret")));    
    userList.add(new JsonObject().put("username", "sonic")
            .put("tenant", "diku")
            .put("permissions", new JsonArray().add("foo.secret").add("bar.secret")));
    userList.add(new JsonObject().put("username", "knuckles")
            .put("tenant", "diku")
            .put("permissions", new JsonArray().add("bar.secret")));
    userList.add(new JsonObject().put("username", "tails")
            .put("tenant", "diku")
            .put("permissions", new JsonArray()));
    userList.add(new JsonObject().put("username", "eggman")
            .put("tenant", "diku")
            .put("permissions", new JsonArray().add("master")));
    ArrayList<Future> futureList = new ArrayList<>();
    for(JsonObject permObj : permissionList) {
      Future<Void> insertFuture = Future.future();
      futureList.add(insertFuture);
      mongoClient.insert("permissions", permObj, res -> {
        if(res.succeeded()) {
          insertFuture.complete();
        } else {
          insertFuture.fail(res.cause());
        }
      });
    }
    for(JsonObject userObj : userList) {
      Future<Void> insertFuture = Future.future();
      futureList.add(insertFuture);
      mongoClient.insert("users", userObj, res-> {
        if( res.succeeded()) { insertFuture.complete(); }
        else { insertFuture.fail(res.cause()); }
      });
    }
    CompositeFuture allInsertsFuture = CompositeFuture.all(futureList);
    allInsertsFuture.setHandler(res -> {
      if(res.succeeded()) {
        async.complete();
      } else {
        context.fail();
      }
    });
  }
  
  @Test
  public void basicPermissionTest(TestContext context) {
    final Async async = context.async();
    store.getExpandedPermissions("master", tenant).setHandler(res -> {
      if(!res.succeeded()) {
        context.fail();
      } else {
        JsonArray result = res.result();
        String[] permCheck = { "foo.secret", "blip.secret", "flop.secret", "master", "foobar" };
        for(String perm : permCheck) {
          //context.assertTrue(result.contains(perm));
          
          if(!result.contains(perm)) {
            context.fail("Result array does not contain '" + perm + "'");
          }
          
        }
        async.complete();
      }
    });
  }
  
  @Test
  public void deleteSubPermissionTest(TestContext context) {
    final Async async = context.async();
    store.removeSubPermission("foobar", "foo.secret", tenant).setHandler(res -> {
      if(!res.succeeded()) {
        context.fail();
      } else {
        store.getExpandedPermissions("foobar", tenant).setHandler(res2 -> {
          if(!res2.succeeded()) {
            context.fail();
          } else {
            JsonArray result = res2.result();
            context.assertFalse(result.contains("foo.secret"));
            async.complete();
          }
        });
      }
    });
  }
  
  @Test
  public void deletePermissionTest(TestContext context) {
    final Async async = context.async();
    store.removePermission("foo.secret", tenant).setHandler(res -> {
      if(!res.succeeded()) {
        context.fail();
      } else {
        store.getExpandedPermissions("master", tenant).setHandler(res2 -> {
          if(!res2.succeeded()) {
            context.fail();
          } else {
            JsonArray result = res2.result();
            context.assertFalse(result.contains("foo.secret"));
            async.complete();
          }
        });
      }
    });
  }
  
  @Test
  public void deleteUserPermissionTest(TestContext context) {
    final Async async = context.async();
    store.getPermissionsForUser("sonic", tenant).setHandler(res -> {
      if(res.failed()) { context.fail(res.cause()); }
      else {
        context.assertTrue(res.result().contains("foo.secret"));
        store.removePermissionFromUser("sonic", "foo.secret", tenant).setHandler(res2 -> {
          if(res2.failed()) { context.fail(res2.cause()); }
          else {
            store.getPermissionsForUser("sonic", tenant).setHandler(res3 -> {
              if(res3.failed()) { context.fail(res3.cause()); }
              else {
                context.assertFalse(res3.result().contains("foo.secret"));
                async.complete();
              }
            });
          }
        });
      }
    });
  }
  
  @Test
  public void addUserPermissionTest(TestContext context) {
    final Async async = context.async();
    store.addPermissionToUser("sonic", "dummy.dummy", tenant).setHandler(res -> {
      if(res.failed()) {
        context.fail(res.cause().getMessage());
      } else {
        store.getPermissionsForUser("sonic", tenant, false).setHandler(res2 -> {
          if(res2.failed()) {
            context.fail(res2.cause().getMessage());
          } else {
            context.assertTrue(res2.result().contains("dummy.dummy"));
            async.complete();
          }
        });
      }
    });
  }
  
  @Test
  public void userPermissionTest(TestContext context) {
    final Async async = context.async();
    store.getPermissionsForUser("eggman", tenant).setHandler(res -> {
      if(res.failed()) { context.fail(res.cause()); } else {
        JsonArray permissions = res.result();
        context.assertTrue(permissions.contains("master"));
        context.assertTrue(permissions.contains("foobar"));
        context.assertTrue(permissions.contains("foo.secret"));
        async.complete();
      }
    });
  }
  
  @Test 
  public void createPermissionTest(TestContext context) {
    final Async async = context.async();
    store.addPermission("spin", tenant).setHandler(res -> {
      if(res.failed()) {
        context.fail("Can't add permission: " + res.cause().getMessage());
      } else {
        store.addSubPermission("spin", "twitch", tenant).setHandler(res2 -> {
          if(res2.failed()) {
            context.fail("Can't add sub permission: " + res2.cause().getMessage());
          } else {
            store.getSubPermissions("spin", tenant).setHandler(res3 -> {
              if(res3.failed()) { context.fail("Unable to get subpermissions " + res3.cause().getMessage()); } else {
                context.assertTrue(res3.result().contains("twitch"));
                async.complete();
              }
            });
          }
        });
      }
    });
  }
  
  @Test
  public void createAndDeletePermissionTest(TestContext context) {
    final Async async = context.async();
    store.addPermission("bean", tenant).setHandler(res -> {
      if(res.failed()) { context.fail("Unable to add permission: " + res.cause().getMessage()); } else {
        store.addPermission("legume", tenant).setHandler(res2 -> {
          if(res2.failed()) { context.fail("Unable to add permission: " + res2.cause().getMessage()); } else {
            store.addSubPermission("legume", "bean", tenant).setHandler(res3 -> {
              if(res3.failed()) { context.fail("Unable to assign subpermission: " + res3.cause().getMessage()); } else {
                store.removePermission("legume", tenant).setHandler(res4 -> {
                  if(res4.failed()) { context.fail("Unable to remove permission: " + res4.cause().getMessage()); } else {
                    async.complete();
                  }
                });
              }
            });
          }
        });
      }
    });
  }
  
  @Test
  public void testGetPermission(TestContext context) {
    final Async async = context.async();
    store.getPermission("master", tenant).setHandler(res -> {
      if(res.failed()) {
        context.fail("Unable to get permission: " + res.cause().getMessage());
      } else {
        JsonObject permission = res.result();
        JsonArray subs = permission.getJsonArray("sub_permissions");
        context.assertNotNull(subs);
        context.assertTrue(subs.contains("foobar"));
        async.complete();
      }
    });
  }
  
  @Test
  public void testGetUser(TestContext context) {
    final Async async = context.async();
    store.getUser("eggman", tenant).setHandler(res -> {
      if(res.failed()) {
        context.fail("Unable to get user: " + res.cause().getMessage());
      } else {
        JsonObject user = res.result();
        context.assertNotNull(user.getJsonArray("permissions"));
        async.complete();
      }
    });
  }

}

