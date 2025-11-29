# Terminal 1 (zookeeper)
mvn clean compile

# Terminal 2 (coordenador)
cd mercado-coordenador  
mvnd exec:java -Dexec.mainClass="br.edu.projeto.coordenador.MainCoordenador"  

# Terminal N (filiais)
cd mercado-filial/
mvnd exec:java -Dexec.mainClass="br.edu.projeto.filial.MainFilial"   

# Terminal 3
cd restaurante
mvnd exec:java -Dexec.mainClass="br.edu.projeto.restaurante.server.Servidor"    

# Terminal 4
cd restaurante
mvnd dependency:copy-dependencies    
java -cp "target/classes:target/dependency/*" br.edu.projeto.restaurante.client.ClienteRestaurante 