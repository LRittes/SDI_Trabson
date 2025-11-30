# Set up

Desativar o firewall temporariamente nas máquinas

MainMercado.java: Deve ler System.getenv("ZOOKEEPER_HOST").

FilialService.java: Deve ler System.getenv("MY_HOST_IP") na hora de criar a String dadosEndereco para o Zookeeper. (Isso é vital!).

Servidor.java (Restaurante): Deve ler System.getenv("RMI_HOSTNAME") e setar java.rmi.server.hostname.

# Terminal 1 (zookeeper)
docker container run --name zookeeper -p 2181:2181 -d zookeeper 
mvn clean compile

# Terminal N (mercado)
cd mercado-coordenador  
export ZOOKEEPER_HOST=<IP maquina zook>:2181  
export MY_HOST_IP=<IP maquina atual>
mvn exec:java -Dexec.mainClass="br.edu.projeto.coordenador.MainMercado"

# Terminal 3
cd restaurante
export RMI_HOSTNAME=<IP maquina atual>
export ZOOKEEPER_HOST=<IP maquina zook>:2181 
mvn exec:java -Dexec.mainClass="br.edu.projeto.restaurante.server.Servidor"    

# Terminal 4
cd restaurante
mvn dependency:copy-dependencies    
java -cp "target/classes:target/dependency/*" br.edu.projeto.restaurante.client.ClienteRestaurante 
