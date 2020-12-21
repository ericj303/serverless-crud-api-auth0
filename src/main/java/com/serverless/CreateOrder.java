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
