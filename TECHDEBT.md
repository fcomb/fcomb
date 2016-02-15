# Tech Debt

## Security

* Custom host ssl verifier for docker nodes (`ssl-config.hostnameVerifierClass`)

## Node

* Check `available` node IP address for uniqueness and resolve conflicts
* Add container messages queue for sequential command applying
* Queue for container actions (start, stop, terminate, restart)
* Add terminating state to container before terminating and terminate it only after successful termination on docker node

## Remote

* Guaranteed delivery of container `state` changes
