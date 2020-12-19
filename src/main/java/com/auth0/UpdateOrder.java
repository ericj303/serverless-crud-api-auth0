package com.auth0;

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
import com.auth0.model.Order;
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
        return updateOrder((String) input.get("body"));  
	}

    private ApiGatewayResponse updateOrder(String body) {
        if (body == null) {
	        System.err.println("body is null!");
            return ApiGatewayResponse.builder().setStatusCode(400).build();
	    }
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(orderTable);

        try {
            JsonNode jn = new ObjectMapper().readTree(body);
            String flavor = jn.get("Flavor").asText();
            String id = jn.get("Id").asText();
            System.out.println("update id: " + id);

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
