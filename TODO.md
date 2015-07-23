# fcomb

Tasks:

* Microservice resource (register, settings, stats, api proxy/gateway)
* Integrate Stripe API
* AWS tokens/sign in
* User API token for calling RPC from microservices
* API proxy and router
* Billing and realtime prediction of user balance
* Directory of microservices API
* Search of microservices API



## Ideas

* Own page for comb with custom domain (like gumroad or github pages)
* Event bus with pub/sub - microservice received email then subscriber read it and do trigger another event

http://microservices.io/patterns/apigateway.html:

* Reduces the number of requests/roundtrips. For example, the API gateway enables clients to retrieve data from multiple services with a single round-trip. Fewer requests also means less overhead and improves the user experience. An API gateway is essential for mobile applications.
* Simplifies the client by moving logic for calling multiple services from the client to API gateway

## Pros

* GPL library as a microservice (cryptolib for example) can be used in closed source product without sharing the code!
