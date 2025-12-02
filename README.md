# Terminal 1 (zookeeper)
mvn clean package -DskipTests
docker compose up -d --build
docker container exec zoo1 bin/zkServer.sh status

# Terminal N
export ZOOKEEPER_HOST=<IP_ZK>:2181,<IP_ZK>:2182,<IP_ZK>:2183
export MY_HOST_IP=<IP_PC>
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  -Dcom.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize=true \
  -jar mercado.jar

# Terminal Server
mvnd dependency:copy-dependencies  
mvnd exec:java -Dexec.mainClass="br.edu.projeto.restaurante.server.Servidor"

# Terminal Cliente
java -cp "target/classes:target/dependency/*" br.edu.projeto.restaurante.client.ClienteRestaurante 
