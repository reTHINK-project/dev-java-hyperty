FROM java:8

#Install maven
RUN apt-get update
 
RUN apt-get install -y maven

WORKDIR /build

# Dependencies
ADD pom.xml /build/pom.xml
RUN ["mvn", "dependency:resolve"]
RUN ["mvn", "verify"]

# Compile and package jar
ADD src /build/src
RUN ["mvn", "package"]

#Setup Config
ADD server-keystore.jks /build/server-keystore.jks
ADD configuration.xml /build/configuration.xml

EXPOSE 9090
CMD ["mvn", "exec:java"]
