## TL;DR 
AWS is the premier cloud platform, and building secure serverless apps that can handle Internet scale demands is easy with AWS and Auth0.  In this blog you will build a serverless order API for delicious ice cream, combining the new HTTP API option of API Gateway, with Lambda, and DynamoDB.  You will deploy your cloud infrastructure with the Serverless Framework CLI, and use Auth0 to easily secure your API endpoints with JWT Authorizers.

### Prerequisites
Sign up for AWS account: https://aws.amazon.com/free/

Install node and npm: https://nodejs.org/en/

Install the Serverless Framework:  https://www.serverless.com/framework/docs/getting-started/

Install Java 8 or above: https://openjdk.java.net/install/

Install Apache Maven: https://maven.apache.org/download.cgi

### Introduction
First you will create a combination of public and secured API endpoints, to see the available ice cream flavors and to list, create, update and delete orders.  

You’ll be using three AWS services:

- DynamoDB: a NoSQL database to store your orders.

- Lambda: your serverless functions running the backend business logic that powers the API.

- API Gateway: the API endpoint service that authenticates requests with Auth0 as needed, and then forwards them to the Lambdas.

Here's the architecture of the app you will build:
![ArchDiagram](https://github.com/ericj303/serverless-crud-api-auth0/blob/main/images/Serverless-CRUD-API-Auth0.png)

The request flow is as follows:

1. HTTP requests are sent to API Gateway.
2. If the endpoint requires JWT Authorization, the JWT token in the request is sent to Auth0 for validation, which then returns the validation result.
3. Authorized requests are then forwarded to one of the backend Lambdas, invoking the relevant business logic.
4. If needed, the Lambda will read or write data from the DynamoDB order table.
5. The Lambda finishes executing and returns a JSON object to API Gateway.
6. API Gateway returns the HTTP response to the requesting application.

### Maven Pom.xml Setup
Start with creating the structure of your Serverless project, by opening a terminal and running:

`serverless create --template aws-java-maven --name icecream-api -p icecream-api`

Open the pom.xml and replace the app metadata:
```
  <groupId>com.serverless</groupId>
  <artifactId>icecream-api</artifactId>
  <packaging>jar</packaging>
  <version>dev</version>
  <name>icecream-api</name>
```
Next add the DynamoDB library for our read/write calls to the `<dependencies>` section:

```
    <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-java-sdk-dynamodb</artifactId>
      <version>1.11.920</version>
    </dependency>
```
Save your changes and run `mvn clean package` to install all the dependencies.  

### serverless.yml Configuration
Open the serverless.yml file, deleting the initial contents and pasting in:
```
service: icecream-api
frameworkVersion: '2'

provider:
  name: aws
  runtime: java8
  stage: dev
  region: us-east-2
  iamRoleStatements:
    - Effect: "Allow"
      Action:
        - "dynamodb:*"
      Resource: "*"
package:
  artifact: target/${self:service}-${self:provider.stage}.jar
```
Looking at the `provider` section, you can see we are using AWS, the Java8 runtime, and our API Gateway stage will be named dev.  Change the region if you don't want to use `us-east-2`.  Next are the IAM policy statements to allow all DynamoDB actions on any tables in the region.  The `httpApi` section contains the information needed for Auth0 to validate your access tokens.  We will come back and fill this out later. 

Moving on to the functions, add the following block to create a endpoint to return the list of available flavors.

```
functions:
  ListFlavors:
    handler: com.serverless.ListFlavors
    events:
      - httpApi:
          path: /flavors
          method: get
```
The handler is the class name of the Lambda code for the function.  The events section is our HTTP API path off the root url, and the GET method will be used.

Next we add the order-related Lambdas.  Notice these have an authorizer in the `httpApi` section, marking them for JWT authorization.
```
  ListOrders:
    handler: com.serverless.ListOrders
    events:
      - httpApi:
          path: /orders
          method: get
          authorizer: serviceAuthorizer
  GetOrder:
    handler: com.serverless.GetOrder
    events:
      - httpApi:
          path: /orders/{id}
          method: get
          authorizer: serviceAuthorizer
  CreateOrder:
    handler: com.serverless.CreateOrder
    events:
      - httpApi:
          path: /orders
          method: post
          authorizer: serviceAuthorizer
  UpdateOrder:
    handler: com.serverless.UpdateOrder
    events:
      - httpApi:
          path: /orders/{id}
          method: put
          authorizer: serviceAuthorizer
  DeleteOrder:
    handler: com.serverless.DeleteOrder
    events:
      - httpApi:
          path: /orders/{id}
          method: delete
          authorizer: serviceAuthorizer
```
As this is YAML, make sure they are all indented to the same level as the ListFlavors function.  As the names indicate, ListOrders will return all orders, GetOrder a single order, CreateOrder makes a new one, UpdateOrder allows the flavor of an order to be changed, and DeleteOrder removes it.

You need a place for your data, so next add a DynamoDB table.  Paste the following `resources` section, with `resources` key word aligned to the far left, like the `functions` and `provider` ones.
```
resources:
  Resources:
    ordersTable:
      Type: AWS::DynamoDB::Table
      Properties:
        TableName: orders
        AttributeDefinitions:
          - AttributeName: Id
            AttributeType: S
        KeySchema:
          - AttributeName: Id
            KeyType: HASH
        ProvisionedThroughput:
          ReadCapacityUnits: 1
          WriteCapacityUnits: 1
```
This creates an `orders` table with a id key of type `String`, provisioned to use the minimal number of read/write capacity units.  Click here for more on [DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Introduction.html).

Now let's add the Auth0 authorization section below to the end of the `providers` section, to configure the Auth0 JWT token support.
```
  httpApi:
    authorizers:
      serviceAuthorizer:
        identitySource: $request.header.Authorization
        issuerUrl: https://<your issuer url here>/
        audience: https://auth0-jwt-authorizer
```
To get the audience and issuerUrl values, log in to your [Auth0 dashboard](https://manage.auth0.com/#/), and click on the "APIs" item in the side menu, then copy the API Audience value of the AWS JWT Authorizer into the audience field, if it's different.  Next click on "Applications" in the side menu, then AWS JWT Authorizer and copy the "Domain" field into the issuerUrl field (make sure you keep the trailing "/" on the URL).

### Add Some Lambda Business Logic
Now let's add the Lambda code.  To go to the code directory, run

`cd src/main/java/com/serverless` 


Create the file ListFlavors.java and insert the following code:
```
package com.serverless;

import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.serverless.model.Flavor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class ListFlavors implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {
    private static final Logger LOG = LogManager.getLogger(Handler.class);

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);
		Flavor[] flavorList = listFlavors();
        if (flavorList != null) {
            return ApiGatewayResponse.builder().setStatusCode(200).setObjectBody(flavorList).build();
        } else {
            return ApiGatewayResponse.builder().setStatusCode(500).build();
        }
	}

    private Flavor[] listFlavors() {
        String[] flavors= { 
            "Chocolate", 
            "Vanilla", 
            "MintChocolate",
            "BubbleGum", 
            "Pistachio", 
            "RockyRoad",
            "Raspberry",
            "Mango",
            "CherryJubilee",
            "Lime"
        };

        Flavor[] flavorList = new Flavor[flavors.length];

        for (int i = 0; i < flavors.length; i++) {
            Flavor f = new Flavor();
            f.setFlavor(flavors[i]);
            flavorList[i] = f;
        }

        return flavorList;
    }
}

```
All the Lambdas have a handleRequest method, which takes in the API Gateway input as a Map, which contains any arguments or POST/PUT data body needed.  This Lambda generates a list of ice cream flavors to return to the user.  The Serverless Framework conveniently handles converting returned data to JSON format.

Create the file ListOrders.java and insert the following code:
```
package com.serverless;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.serverless.model.Order;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Map;

public class ListOrders implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {
    private static final String orderTable = "orders";
    private static final Logger LOG = LogManager.getLogger(Handler.class);

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);
		Order[] orders = listOrders();
        if (orders != null) {
            return ApiGatewayResponse.builder().setStatusCode(200).setObjectBody(orders).build();
        } else {
            return ApiGatewayResponse.builder().setStatusCode(500).build();
        }
	}

    private Order[] listOrders() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(orderTable);

        ScanSpec scanSpec = new ScanSpec().withProjectionExpression("Id, Customer, Flavor");
        List<Order> orders = new ArrayList<Order>();

        try {
            ItemCollection<ScanOutcome> items = table.scan(scanSpec);
            Iterator<Item> iter = items.iterator();
            while (iter.hasNext()) {
                Item item = iter.next();
                Order newOrder = new Order();
                newOrder.setId(item.getString("Id"));
                newOrder.setCustomer(item.getString("Customer"));
                newOrder.setFlavor(item.getString("Flavor"));
                orders.add(newOrder);
            }
        }
        catch (Exception e) {
            System.err.println("Unable to scan the table:");
            System.err.println(e.getMessage());
            return null;
        }

        return orders.stream().toArray(Order[]::new);
    }
}

```
The AmazonDynamoDBClientBuilder is used for it's ease of use.  This Lambda runs a DynamoDB scan of table items, returning all orders in the table.

Make the file GetOrder.java and paste the following code:
```
package com.serverless;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.serverless.model.Order;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Map;

public class GetOrder implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {
    private static final String orderTable = "orders";
    private static final Logger LOG = LogManager.getLogger(Handler.class);

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);
        Map<String,String> pathParameters =  (Map<String,String>)input.get("pathParameters");
        String id = pathParameters.get("id");
		Order order = getOrder(id);
        if (order != null) {
            return ApiGatewayResponse.builder().setStatusCode(200).setObjectBody(order).build();
        } else {
            return ApiGatewayResponse.builder().setStatusCode(500).build();
        }
	}

    private Order getOrder(String orderId) {
        System.out.println("orderId: " + orderId);
	    AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(orderTable);

        GetItemSpec spec = new GetItemSpec().withPrimaryKey("Id", orderId);
	    Item item = null;
        try {
            System.out.println("Attempting to read the item...");
            item = table.getItem(spec);
            System.out.println("GetItem succeeded: " + item);
        }
        catch (Exception e) {
            System.err.println("Unable to read item: " + orderId);
            System.err.println(e.getMessage());
            return null;
        }

        Order order = new Order();
        order.setId(orderId);
        order.setCustomer(item.getString("Customer"));
        order.setFlavor(item.getString("Flavor"));
        return order;
    }
}


```
This Lambda gets an order by primary key, the id, and returns it.

Next create the file CreateOrder.java and insert the following code:
```
package com.serverless;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;

public class CreateOrder implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {
    private static final String orderTable = "orders";
    private static final Logger LOG = LogManager.getLogger(Handler.class);

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);
		return createOrder((String) input.get("body"));
	}

    private ApiGatewayResponse createOrder(String body) {
        if (body == null) {
	        System.err.println("body is null!");
            return ApiGatewayResponse.builder().setStatusCode(400).build();
	    }
        
        String customer = "";
        String flavor = "";
        try {
            JsonNode jn = new ObjectMapper().readTree(body);
            customer = jn.get("Customer").asText();
            flavor = jn.get("Flavor").asText();

            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
            DynamoDB dynamoDB = new DynamoDB(client);
            Table table = dynamoDB.getTable("orders");

            System.out.println("Adding a new item...");
            PutItemOutcome outcome = table
                .putItem(new Item().withPrimaryKey("Id", UUID.randomUUID().toString())
                .withString("Customer", customer)
                .withString("Flavor", flavor));

            System.out.println("PutItem succeeded:\n" + outcome.getPutItemResult());
        }
        catch (Exception e) {
            System.err.println("Unable to add item: " + customer + " " + flavor);
            System.err.println(e.getMessage());
            return ApiGatewayResponse.builder().setStatusCode(500).build();
        }

        return ApiGatewayResponse.builder().setStatusCode(201).build();
    }
}

```
This code adds the new order to our NoSQL DynamoDB database!  Because of it's NoSQL nature, we didn't have to define a table schema up front, but can simply add the customer and flavor columns on the fly.

Add the file UpdateOrder.java and insert the following code:
```
package com.serverless;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.serverless.model.Order;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class UpdateOrder implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {
    private static final String orderTable = "orders";
    private static final Logger LOG = LogManager.getLogger(Handler.class);

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);
        Map<String,String> pathParameters =  (Map<String,String>)input.get("pathParameters");
        String id = pathParameters.get("id");
        return updateOrder(id, (String) input.get("body"));  
	}

    private ApiGatewayResponse updateOrder(String id, String body) {
        if (id == null || body == null) {
	        System.err.println("id or body is null!");
            return ApiGatewayResponse.builder().setStatusCode(400).build();
	    }
        System.out.println("update id: " + id);

        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(orderTable);

        try {
            JsonNode jn = new ObjectMapper().readTree(body);
            String flavor = jn.get("Flavor").asText();

            UpdateItemSpec updateItemSpec = new UpdateItemSpec().withPrimaryKey(new PrimaryKey("Id", id))
                .withUpdateExpression("set Flavor = :val1").withValueMap(new ValueMap().withString(":val1", flavor))
                .withReturnValues(ReturnValue.UPDATED_NEW);

            System.out.println("Updating the item...");
            UpdateItemOutcome outcome = table.updateItem(updateItemSpec);
            System.out.println("UpdateItem succeeded:\n" + outcome.getItem().toJSONPretty());
        }
        catch (Exception e) {
            System.err.println("Unable to update item: " + body);
            System.err.println(e.getMessage());
            return ApiGatewayResponse.builder().setStatusCode(500).build();
        }

		return ApiGatewayResponse.builder().setStatusCode(204).build();
    }
}


```
All flavor changes to an order are done by finding the relevant order by id, and updating the flavor field.

Finally create the last Lambda file, DeleteOrder.java, and add the following code:
```
package com.serverless;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.serverless.model.Order;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Map;

public class DeleteOrder implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {
    private static final String orderTable = "orders";
    private static final Logger LOG = LogManager.getLogger(Handler.class);

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);
        Map<String,String> pathParameters =  (Map<String,String>)input.get("pathParameters");
        String id = pathParameters.get("id");
        return deleteOrder(id);
	}

    private ApiGatewayResponse deleteOrder(String id) {
        System.out.println("delete id: " + id);
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(orderTable);

	    DeleteItemSpec deleteItemSpec = new DeleteItemSpec()
            .withPrimaryKey(new PrimaryKey("Id", id));

        try {
            System.out.println("Attempting delete...");
            table.deleteItem(deleteItemSpec);
            System.out.println("DeleteItem succeeded");
        }
        catch (Exception e) {
            System.err.println("Unable to delete item: " + id);
            System.err.println(e.getMessage());
            return ApiGatewayResponse.builder().setStatusCode(500).build();
        }

        return ApiGatewayResponse.builder().setStatusCode(204).build();
    }
}

```
This deletes orders based on the id argument.

### Create Helper Classes
Some POJO data object classes are needed next.  Create a model directory,

`mkdir model`

and change to it.

`cd model` 

Make the file Flavor.java and insert the following code:
```
package com.serverless.model;
  
public class Flavor {
    private String flavor;

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }
}
```

Then create the file Order.java and add the following code:
```
package com.serverless.model;
  
public class Order {
    private String id;
    private String customer;
    private String flavor;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }
}
```

Now go back to the root directory of the project

`cd ../../../../..` 

and build the project!

`mvn clean package`

For the BIG deploy, create the AWS infrastructure with

`sls deploy`

You should see output similar to the following:
```
Serverless: Packaging service...
Serverless: Uploading CloudFormation file to S3...
Serverless: Uploading artifacts...
Serverless: Uploading service icecream-api-dev.jar file to S3 (10.5 MB)...
Serverless: Validating template...
Serverless: Updating Stack...
Serverless: Checking Stack update progress...
.................
Serverless: Stack update finished...
Service Information
service: icecream-api
stage: dev
region: us-east-2
stack: icecream-api-dev
resources: 43
api keys:
  None
endpoints:
  GET - https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/flavors
  GET - https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders
  GET - https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders/{id}
  POST - https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders
  PUT - https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders/{id}
  DELETE - https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders/{id}
functions:
  ListFlavors: icecream-api-dev-ListFlavors
  ListOrders: icecream-api-dev-ListOrders
  GetOrder: icecream-api-dev-GetOrder
  CreateOrder: icecream-api-dev-CreateOrder
  UpdateOrder: icecream-api-dev-UpdateOrder
  DeleteOrder: icecream-api-dev-DeleteOrder
layers:
  None
  ```
### Test It Out!

Use the endpoint outputs to get the unique API Gateway id for your API.  Use this to complete the test URLs (ie. the section in front of "execute" in URL https://YOUR-API-ID-HERE.execute-api.us-east-2.amazonaws.com).  Also, change the "us-east-2" region part of the URL, if you selected a different region.  If you receive a "HTTP/1.1 503 Service Unavailable" on your first curl run for an endpoint, rerun curl again.

- Test getting all the ice cream flavors.  No authorization will be required here.

    `curl -i https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/flavors`
    
    You should receive a 200 OK response and a list of flavors.
<br>
- The order endpoints will all require an authorization header with an access token.  Try getting all orders without the auth header to see it is secured.  

    `curl -i  https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders`

    You'll receive a 401 response code and "Unauthorized" message.  
    
    Now go to your [Auth0 dashboard](https://manage.auth0.com/#/), and click on the "APIs" item in the side menu, then the "AWS JWT Authorizer", select the "Test" tab.  Go down to the "Sending the token to the API" section, and copy the "--header 'authorization: Bearer <access token...>" text.  IMPORTANT:  Attach this to the end of all remaining order API calls.

    Make another test call with the auth header attached.  You should receive a 200 OK and "[]" empty array back.
<br>
- Now it's time to POST a new ice cream order!

    `curl -i  -H "Content-Type: application/json" -d '{"Customer":"Sue","Flavor":"BubbleGum"}' -X POST https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders`

    You should receive a 201 Created response.
<br>
- Update the ice cream flavor on the order.  Run the get all orders curl to get the created order id.

    `curl -i  https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders`

    Use the id field value in your PUT update call, replacing the "{id}" at end of URL.

    `curl -i  -H "Content-Type: application/json" -d '{"Flavor":"chocolateChip"}' -X PUT https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders/{id}`

    You should receive a 204 No Content response.
<br>
- Check to see the flavor was changed on the order, by getting the order by id, replacing the "{id}".

    `curl -i  https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders/{id}`

    You should receive a 200 OK and "chocolateChip" for flavor value.
<br>
- Delete the order, using the correct id value at end of URL.

    `curl -i  https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders/{id} -X DELETE`

    You should receive a 204 No Content response.
<br>
- Get all orders and see the order is gone.

    `curl -i  https://8qbtim49n6.execute-api.us-east-2.amazonaws.com/orders`

    You should receive a 200 OK and "[]" empty array back.
<br>
- Undeploy the app

    `sls remove`


### Summary
AWS is amazing platform to build highly scalable serverless apps.  The Serverless Framework allows best practice IaC (Infrastructure-as-Code) deployment of sophisticated cloud infrastructure combining API Gateway, Lambda and DynamoDB services.  It has many nice features, such as automatically converting your returned data to JSON format, and many plugins are available for additional functionality.  Also, authorizing your endpoints is easy with the new HTTP API's JWT Authorizer functionality, and a identity provider like Auth0.  

Enjoy building on AWS!







