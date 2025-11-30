# Terminal 1 (zookeeper)
docker container run --name zookeeper -p 2181:2181 -d zookeeper 
mvn clean compile

# Terminal N (mercado)
cd mercado-coordenador  
mvn exec:java -Dexec.mainClass="br.edu.projeto.coordenador.MainMercado"

# Terminal 3
cd restaurante
mvn exec:java -Dexec.mainClass="br.edu.projeto.restaurante.server.Servidor"    

# Terminal 4
cd restaurante
mvn dependency:copy-dependencies    
java -cp "target/classes:target/dependency/*" br.edu.projeto.restaurante.client.ClienteRestaurante 
