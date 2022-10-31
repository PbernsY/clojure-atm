FROM openjdk:20-oraclelinux8
WORKDIR /usr/src/clojure-atm
COPY clojure-atm.jar /usr/src/clojure-atm
ENTRYPOINT ["java", "-jar", "clojure-atm.jar"]

