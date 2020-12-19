package com.auth0;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.auth0.model.Order;
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
