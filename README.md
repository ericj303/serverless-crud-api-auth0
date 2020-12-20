# serverless-crud-api-auth0
Serverless Framework CRUD API in Java, using Auth0 to secure order endpoints.

#Prereqs

Sign up for AWS account https://aws.amazon.com/free/

Install node and npm https://nodejs.org/en/

Install the Serverless Framework  https://www.serverless.com/framework/docs/getting-started/

Install Java 8 https://openjdk.java.net/install/

Install Apache Maven https://maven.apache.org/download.cgi

# To build and test the app

- mvn clean package

- Deploy the app:  sls deploy

- Get flavors - no auth needed

    curl -i https://nz94nnimbe.execute-api.us-east-2.amazonaws.com/flavors

- Everything else needs auth header.  If receive a "HTTP/1.1 503 Service Unavailable", just run curl again.

- POST new order

    curl -i  -H "Content-Type: application/json" -d '{"Customer":"Sue","Flavor":"BubbleGum"}' -X POST https://nz94nnimbe.execute-api.us-east-2.amazonaws.com/orders

- Get all orders

    curl -i  https://nz94nnimbe.execute-api.us-east-2.amazonaws.com/orders

- Update flavor on order, update id

    curl -i  -H "Content-Type: application/json" -d '{"Flavor":"chocolateChip"}' -X PUT https://nz94nnimbe.execute-api.us-east-2.amazonaws.com/orders/1

- Delete by id

    curl -i  https://nz94nnimbe.execute-api.us-east-2.amazonaws.com/orders/1 -X DELETE

- Get all orders and see it's deleted

    curl -i  https://nz94nnimbe.execute-api.us-east-2.amazonaws.com/orders

- Undeploy the app:  sls remove
