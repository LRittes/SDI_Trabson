package br.edu.projeto.restaurante.server;

import br.edu.projeto.interfaces.*; // Importa Administrador, Restaurante, etc.

import javax.xml.ws.Service;
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

     @Override
    public void fazerComprasNoMercado(String nomeRestaurante, String[] produtos, ClienteCallback cliente) throws RemoteException {
        System.out.println("ADMIN: Recebida solicitação de compras. Conectando ao Mercado...");
        try {
            URL wsdlUrl = new URL("http://localhost:8080/mercado?wsdl");
            // O namespace deve ser EXATAMENTE o que apareceu no erro
            QName serviceQName = new QName("http://coordenador.projeto.edu.br/", "MercadoImplService");
            Service serviceFactory = Service.create(wsdlUrl, serviceQName);
            MercadoServidor mercado = serviceFactory.getPort(MercadoServidor.class);

            int pedidoId = mercado.cadastrarPedido(nomeRestaurante);
            mercado.comprarProdutos(pedidoId, produtos);
            
            System.out.println("ADMIN: Compra para o pedido #" + pedidoId + " realizada. Iniciando monitoramento da entrega.");

            new Thread(() -> {
                monitorarEntregaMercado(mercado, pedidoId, cliente);
            }).start();

        } catch (Exception e) {
            System.err.println("ADMIN: Erro ao conectar com o mercado!");
            e.printStackTrace();
            try {
                cliente.notificarEntregaMercado("ERRO: Falha ao realizar o pedido no mercado.");
            } catch (RemoteException re) {
                System.err.println("ADMIN: Falha ao notificar cliente sobre o erro do mercado.");
            }
        }
    }

    private void monitorarEntregaMercado(MercadoServidor mercado, int pedidoId, ClienteCallback cliente) {
        try {
            while (true) {
                int tempoRestante = mercado.tempoEntrega(pedidoId);
                System.out.println("MONITOR (Mercado): Verificando pedido #" + pedidoId + ". Tempo restante: " + tempoRestante + "s.");
                
                if (tempoRestante <= 0) {
                    String mensagem = "A entrega do seu pedido de mercado #" + pedidoId + " chegou!";
                    cliente.notificarEntregaMercado(mensagem);
                    break;
                }
                
                Thread.sleep(5000); 
            }
        } catch (Exception e) {
            System.err.println("MONITOR (Mercado): Erro na thread de monitoramento do pedido #" + pedidoId);
            e.printStackTrace();
            try {
                cliente.notificarEntregaMercado("ERRO: Ocorreu uma falha ao monitorar a entrega do pedido #" + pedidoId);
            } catch (RemoteException re) {
            }
        }
    }
}