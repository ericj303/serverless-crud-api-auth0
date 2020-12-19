package com.auth0;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.auth0.model.Order;
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
