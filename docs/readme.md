## Java based Hyperties
### Build status

| Master                                   |
| ---------------------------------------- |
| [![Build Status](https://travis-ci.org/reTHINK-project/dev-java-hyperty.svg?branch=master)](https://travis-ci.org/reTHINK-project/dev-java-hyperty) |


## Overview
Hyperties in java are supported by using the [Vertx framework](http://vertx.io/) and an [abstract java Hyperty verticle](abstract-hyperty.md) that implements some required features.

![Vertx Hyperties Architecture](jvm-abstract-hyperty.png)

Interoperability with javascript core runtime Hyperties are  supported by using a P2P vertx runtime protostub i.e. communication is directly established between the javascript Hyperty runtime and the JVM Hyperty runtime without using the Message Node. The P2P vertx stub uses Vertx Event BUS over Encrypted Web Sockets to connect with JVM Vertx Hyperty Runtime, making the bridge between Vertx Event BUS and Javascript Hyperty Runtime Message BUS.

### Javascript Hyperties -> JVM Vertx Hyperties

The main data flows for Vertx Hyperties Observing Data Objects reported by Javascript Hyperties, are:

![Vertx Hyperties Observing Data Objects reported by Javascript Hyperties](observer-interoperability.png)

### JVM Vertx Hyperties -> Javascript Hyperties

The main data flows for Vertx Hyperties Reporting Data Objects that are observed by Javascript Hyperties, are:

![Vertx Hyperties Reporting Data Objects that are observed by Javascript Hyperties](reporter-interoperability.png)

## Protostubs

This is a [reTHINK protostub like](https://rethink-project.github.io/specs/concepts/protofly/) component to interface with remote Hyperty Runtimes by using some messaging protocol when the connection is triggered by the Vertx Runtime. Currently, Vertx protostubs are not published in reTHINK Catalogue from where they are dynamically deployed on Vertx Hyperty Runtime. Instead, they are implemented and deployed as Vertx Verticles. *should we also have an Abstract Protostub?*

Remote addresses that are interfaced with protostubs are set as a Verticle configuration in an array. Event BUS handlers are set with these addresses to forward Vertx Hyperty messages to remote entities.

## Runtime registry

Use [Vertx service discovery API](http://vertx.io/docs/vertx-service-discovery) to register and discover Hyperties and Data Objects  using a JSON object compliant with [reTHINK Hyperty Instance Data Model](https://rethink-project.github.io/specs/datamodel/core/hyperty-registry/readme/).



## Start Java Runtime Hyperties

We can use the docker-hub image, that is available [here](https://hub.docker.com/r/rethinkaltice/dev-java-hyperty/), to start JavaRuntime. 

With docker-compose you can use the following example:


    'dev-java-hyperty':
    	image: 'rethinkaltice/dev-java-hyperty:develop'
    	container_name: "dev-java-hyperty"
        environment:
          - MONGOHOSTS=MongoIP
          - MONGOPORTS=27017
          - MONGO_CLUSTER=NO
          - LOG_LEVEL=INFO
          - SCHEDULE_MINUTE=0
          - SCHEDULE_HOUR=2
          - SIOT_POC=https://vertx-runtime.rethink.alticelabs.com/requestpub
          - CHALLENGE_EXPIRE=1559257200000
        networks:
          rethink:
            ipv4_address: 172.20.0.128
        expose:
          - '443'
          - '9091'
It is available some environment variables to be used on startUp of container

- MONGOHOSTS: could be more than one MONGOHOSTS separated by comma:IP1,IP2
- MONGOPORTS: could be more than one MONGOPORTS separated by comma:PORT1,PORT2
- MONGO_CLUSTER: should be YES, if you are using more than one MONGOHOST
- LOG_LEVEL: you can alternate between INFO and DEBUG
- SCHEDULE_MINUTE, SCHEDULE_HOUR: SCHEDULE is used to clear expired accounts transactions
- SIOT_POC: This URL is to be used for smartIOT protostub
- CHALLENGE_EXPIRES: Long timestamp used to control transactions added to public wallets



### Mongo Database
There is some collections, that we must create and fill to the application use like shops, elearnings, bonus and agentsList

#### Shops Collection
Here we can add documents, where which one will be a shop, the skeleton of shop document will be like this:


```json
{
	"id": "shopID",
	"name": "Shop Name",
	"description": "description name of shop",
	"picture": "picture URL",
	"opening-hours": {
		"monday-saturday": "[10:00-13:00, 15:00-19:30]"
	},
	"location": {
		"degrees-latitude": 1.0,
		"degrees-longitude": -1.0
	}
}
```
#### Bonus Collection
Here we can add documents, where which one will be a bonus associated with a shop, the skeleton of the bonus document will be like this:


```json
{
	"id": "bonusID",
	"name": "name of bonus",
	"description": " description of bonus",
	"cost": 300,
	"spotID": "shopID",
	"constraints": {
		"period": "day",
		"times": 1
	},
	"icon": "https://urlforicon.jpg",
	"start": "2018/08/10",
	"expires": "2019/05/31",
	"successfulTransactionIcon": "https://success.jpg",
	"failedTransactionIcon": "https://error.jpg"
}
```


#### Elearnigs Collection

Here we can add documents, where which one will be a elearnig(quizz), the skeleton of elearnig document will be like this:


```json
{
	"type": "default | mini-quiz | power-quiz",
	"name": "Quizz name",
	"description": "",
	"picture": "",
	"classification": "Nível 1 | Nível 2 | Nível 3",
	"date": "2018-09-01",
	"category": "energia | mobilidade",
	"value": 65,
	"questions": [{
		"id": 1,
		"question": "Question 1?",
		"answers": ["answer 1", "answer 2", "answer 3"],
		"correctAnswer": 0,
		"hint": "hint description"
	}, {
		"id": 2,
		"question": "Question 2?",
		"answers": ["answer 1", "answer 2", "answer 3"],
		"correctAnswer": 1,
		"hint": "hint description"
	}, {
		"id": 3,
		"question": "Question 3?",
		"answers": ["answer 1", "answer 2", "answer 3"],
		"correctAnswer": 2,
		"hint": "hint description"
	}]
}
```



Type property could be: default or mini-quiz or power-quiz

Classification property could be: Nível 1 or Nível 2 or Nível 3

Category property could be: energia or mobilidade

#### AgentsList Collection

Here we can add documents, where which one will be an agent to receive tickets and handle them.

```json
	{
	  "address": "agentAddress",
	  "code": "agentCode"
	}
```
