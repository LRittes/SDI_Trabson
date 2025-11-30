# Usa imagem base leve do Java 11
FROM eclipse-temurin:11-jre-alpine

# Define diretório de trabalho dentro do container
WORKDIR /app

# Copia o JAR gerado (ajuste o caminho se necessário)
COPY mercado-coordenador/target/mercado-coordenador-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar

# Copia o arquivo CSV para a raiz do container (simulando o "root" do projeto)
COPY menu_restaurante.csv /menu_restaurante.csv

# O CSV no código Java é lido como "../menu_restaurante.csv". 
# Como estamos em /app, subir um nível (..) leva para a raiz /, onde o arquivo está.

# Expõe a porta SOAP (8080) e uma faixa para Sockets
EXPOSE 8080
EXPOSE 4000-5000

# Comando para iniciar
ENTRYPOINT ["java", "-jar", "app.jar"]