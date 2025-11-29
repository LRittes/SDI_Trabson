package com.example.demo;

import javax.xml.ws.Endpoint;


/**
 * Esta classe é responsável por iniciar o servidor e publicar
 * nosso WebService em um endereço de rede.
 */
public class Publicador {
    public static void main(String[] args) {
        // Define a URL onde o serviço ficará disponível.
        // Você pode usar 'localhost' ou '0.0.0.0' para aceitar conexões de outras máquinas na rede.
        String url = "http://localhost:9000/mercado";

        System.out.println("Iniciando o WebService SOAP...");
        
        // Publica a implementação do serviço na URL definida.
        Endpoint.publish(url, new MercadoServidorImpl());

        System.out.println("======================================================================");
        System.out.println("Serviço SOAP no ar!");
        System.out.println("Endpoint disponível em: " + url);
        System.out.println("Para ver o WSDL (contrato do serviço), acesse: " + url + "?wsdl");
        System.out.println("Pressione CTRL+C para encerrar o servidor.");
        System.out.println("======================================================================");
    }
}
