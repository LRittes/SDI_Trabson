# Terminal 1
cd rmi/
mvn clean
mvn compile
mvn exec:java -Dexec.mainClass="server.Servidor"

# Terminal 2
cd rmi/
mvn exec:java -Dexec.mainClass="client.ClienteRestaurante"

# Terminal 3
cd demo/
mvn clean
mvn exec:java -Dexec.mainClass="com.example.demo.Publicador"