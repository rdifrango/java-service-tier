FROM amazoncorretto:17
MAINTAINER ron.difrango@gmail.com
EXPOSE 8080
WORKDIR /data
CMD java -jar gs-accessing-data-rest-0.1.0.jar
ADD target/gs-accessing-data-rest-0.1.0.jar /data/gs-accessing-data-rest-0.1.0.jar
ADD src/main/resources/application.properties /data/application.properties
ADD src/main/resources/AwsCredentials.properties /data/AwsCredentials.properties
