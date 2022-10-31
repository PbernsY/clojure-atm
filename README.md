# Clojure ATM

## Building 
Clojure-atm uses https://leiningen.org/ . Once installed , ``lein uberjar`` will build the application. 

This will produce an uberjar named ``clojure-atm.jar-0.1.0-SNAPSHOT-standalone.jar`` in the ``target/uberjar`` directory of the project. A built JAR is attached

## Running 
 Once the JAR is built , the application can be ran by the following command
 ``java -jar clojure-atm.jar-0.1.0-SNAPSHOT-standalone.jar ``

This will start the server listening for incoming requests

## Testing/Usage 

The repository contains test-FUNCTIONALITY files. These can be used by 

``curl localhost:8080/URI -d @test-URI -H "Content-Type: application/json"``

Where URI is one of the following -> *withdraw* , *deposit* , *balance*

The test files contain the structure of the JSON request the API is expecting, modification of these is acceptable and appropriate error messages will be returned if there is a problem with the request
