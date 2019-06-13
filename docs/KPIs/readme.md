# Get KPI's from database

For it, you need to have a version of database on you local computer.

Install [MongoDB](https://docs.mongodb.com/manual/installation/) and get a version of database from remote machine using [mongodump](https://docs.mongodb.com/manual/reference/program/mongodump/) command, and save this version on you local machine using [mongorestore](https://docs.mongodb.com/manual/reference/program/mongorestore/) command. 



When you have a version of remote machine database on you local machine you can get some KPI's from database. For it you can use a environment variable `DATA` for get some data such as:

- `CHECKINS`: with environment variable `DATA` configured as `CHECKINS `you can get data related with checkins by each shop.
- `BONUS`: with environment variable `DATA` configured as `BONUS `you can get data related with bonus by each shop.
- `CODES`: with environment variable `DATA` configured as `CODES `you can get data related with codes used to register new wallets.
- `SOCIOECONOMIC`: with environment variable `DATA` configured as `SOCIOECONOMIC `you can get data related with Socio Economic Indicators.
- `WALLETS`: with environment variable `DATA` configured as `WALLETS `you can get data related with sum of all transactions such as  checkin, elearnings, wallets, walking, biking and bonus.
- `WALLETS-SCHOOL`: with environment variable `DATA` configured as `WALLETS-SCHOOL `you can get data related with sum of all transactions such as  checkin, elearnings, wallets, walking, biking and bonus by school.



To execute it you need to:

- Install the current version of remote machine database on your local machine

- [clone this repository](https://github.com/reTHINK-project/dev-java-hyperty)

- enter on `java-mongo-script` directory

- export DATA environment variable, `export DATA=CHECKINS`

- you can execute it in two different ways. First as a java project, for example, `mvn exec:java`. Second using jar file on [target directory](https://github.com/reTHINK-project/dev-java-hyperty/tree/master/java-mongo-script/target) `java -jar mongo-script-0.0.1.jar`

  ​
