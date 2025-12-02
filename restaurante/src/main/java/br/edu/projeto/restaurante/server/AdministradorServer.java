package br.edu.projeto.restaurante.server;

import br.edu.projeto.interfaces.*; // Importa Administrador, Restaurante, etc.

import javax.xml.ws.Service;

import org.apache.zookeeper.ZooKeeper;

import javax.xml.namespace.QName;

import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class AdministradorServer extends UnicastRemoteObject implements Administrador {
    private final Cozinha cozinha;
    private final Restaurante restaurante;

    protected AdministradorServer(Restaurante restaurante, Cozinha cozinha) throws RemoteException {
        super();
        this.restaurante = restaurante;
        this.cozinha = cozinha;
    }
    
    @Override
    public void encaminharPedido(int comanda, String[] pedido, ClienteCallback cliente) throws RemoteException {
        System.out.println("ADMIN: Recebeu pedido da comanda " + comanda + ". Enviando para cozinha.");
        final int preparoId = cozinha.novoPreparo(comanda, pedido);
        new Thread(() -> {
            try {
                while (cozinha.tempoPreparo(preparoId) > 0) {
                    Thread.sleep(1000);
                }
                String[] pratoPronto = cozinha.pegarPreparo(preparoId);
                if (pratoPronto != null) {
                    restaurante.fazerPedido(comanda, pratoPronto);
                    cliente.notificarPedidoEntregue(pratoPronto);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Método auxiliar para descobrir a URL
    private String descobrirUrlDoLider() throws Exception {
        String zkHost = System.getenv("ZOOKEEPER_HOST"); 
        if (zkHost == null) zkHost = "localhost:2181";

        // Conecta no ZK
        ZooKeeper zkClient = new ZooKeeper(zkHost, 10000, event -> {});
        
        // Espera conectar... (Simplificado)
        while(!zkClient.getState().isConnected()) Thread.sleep(100);

        // Lê os dados do nó /servico-soap
        if (zkClient.exists("/servico-soap", false) != null) {
            byte[] dados = zkClient.getData("/servico-soap", false, null);
            String url = new String(dados);
            zkClient.close();
            return url;
        }
        
        zkClient.close();
        throw new Exception("Nenhum líder registrou a URL do SOAP ainda.");
    }

    @Override
    public void fazerComprasNoMercado(String nomeRestaurante, String[] produtos, ClienteCallback cliente) throws RemoteException {
        try {
            // 1. Pergunta ao Zookeeper onde está o serviço
            System.out.println("ADMIN: Consultando Zookeeper para achar o líder...");
            String urlDinamica = descobrirUrlDoLider();
            System.out.println("ADMIN: Líder encontrado em: " + urlDinamica);

            // 2. Conecta
            URL wsdlUrl = new URL(urlDinamica);
            // ... continua o código normal ...
            QName serviceQName = new QName("http://coordenador.projeto.edu.br/", "MercadoImplService");
            Service serviceFactory = Service.create(wsdlUrl, serviceQName);
            MercadoServidor mercado = serviceFactory.getPort(MercadoServidor.class);

            // 1. Cadastra o pedido
            int pedidoId = mercado.cadastrarPedido(nomeRestaurante);
            
            // 2. Tenta realizar a compra
            // O Coordenador retorna TRUE se reservou tudo, ou FALSE se falhou (não achou estoque)
            boolean compraRealizada = mercado.comprarProdutos(pedidoId, produtos);
            
            if (compraRealizada) {
                System.out.println("ADMIN: Compra #" + pedidoId + " realizada com SUCESSO. Iniciando monitoramento.");
                new Thread(() -> {
                    monitorarEntregaMercado(mercado, pedidoId, cliente);
                }).start();
            } else {
                // SE A COMPRA FALHOU (Retornou false)
                System.out.println("ADMIN: Compra #" + pedidoId + " RECUSADA pelo mercado (Sem estoque).");
                String listaProdutos = String.join(", ", produtos);
                cliente.notificarEntregaMercado("FALHA NA COMPRA: Os itens solicitados (" + listaProdutos + ") não estão disponíveis em nenhuma filial no momento.");
            }

        } catch (Exception e) {
            System.err.println("ADMIN: Erro ao conectar com o mercado!");
            e.printStackTrace();
            try {
                cliente.notificarEntregaMercado("ERRO TÉCNICO: Falha ao comunicar com o Coordenador do Mercado.");
            } catch (RemoteException re) { /* Ignora */ }
        }
    }

    private void monitorarEntregaMercado(MercadoServidor mercado, int pedidoId, ClienteCallback cliente) {
        try {
            while (true) {
                int tempoRestante = mercado.tempoEntrega(pedidoId);
                
                // Tratamento caso o pedido suma ou dê erro durante o monitoramento (-1)
                if (tempoRestante == -1) {
                    System.out.println("MONITOR: Recebido -1 (Pedido não encontrado). Encerrando monitoramento.");
                    cliente.notificarEntregaMercado("ERRO: O pedido #" + pedidoId + " foi cancelado ou não encontrado no mercado.");
                    break;
                }

                System.out.println("MONITOR (Mercado): Verificando pedido #" + pedidoId + ". Tempo restante: " + tempoRestante + "s.");
                
                if (tempoRestante <= 0) {
                    String mensagem = "SUCESSO: A entrega do seu pedido de mercado #" + pedidoId + " chegou no restaurante!";
                    cliente.notificarEntregaMercado(mensagem);
                    break;
                }
                
                Thread.sleep(2000); // Verifica a cada 2 segundos (Polling)
            }
        } catch (Exception e) {
            System.err.println("MONITOR: Erro na thread de monitoramento.");
            e.printStackTrace();
        }
    }
}