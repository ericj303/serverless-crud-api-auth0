package com.auth0;

import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.auth0.model.Flavor;
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
