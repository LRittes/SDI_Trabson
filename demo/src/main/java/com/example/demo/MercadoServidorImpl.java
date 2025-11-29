package com.example.demo;


import javax.jws.WebService;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;
import java.util.Map;

@WebService(endpointInterface = "com.example.demo.MercadoServidor")
public class MercadoServidorImpl implements MercadoServidor {

    private static final AtomicInteger contadorPedidos = new AtomicInteger(0);
    
    private static final Map<Integer, Pedido> pedidos = new ConcurrentHashMap<>();

    @Override
    public int cadastrarPedido(String restaurante) {
        System.out.println(">>> Recebido novo pedido para o restaurante: " + restaurante);
        
        int idPedido = contadorPedidos.incrementAndGet();
        
        Pedido novoPedido = new Pedido(idPedido, restaurante);
        pedidos.put(idPedido, novoPedido);
        
        System.out.println(">>> Pedido cadastrado com sucesso sob o ID: " + idPedido);
        
        return idPedido;
    }

    @Override
    public boolean comprarProdutos(int idPedido, String[] produtos) {
        System.out.println(">>> Recebida solicitação de compra para o pedido ID " + idPedido + "...");
        
        if (!pedidos.containsKey(idPedido)) {
            System.err.println(">>> Falha: Pedido com ID " + idPedido + " não encontrado.");
            return false;
        }
        
        if (produtos == null || produtos.length == 0) {
            System.err.println(">>> Falha: Lista de produtos está vazia.");
            return false;
        }
        
        Pedido pedido = pedidos.get(idPedido);
        
        pedido.setProdutos(produtos);
        
        int duracaoEntregaSegundos = new Random().nextInt(20);
        long tempoConclusao = System.currentTimeMillis() + (duracaoEntregaSegundos * 1000L);
        pedido.setTempoConclusao(tempoConclusao);
        
        System.out.println(">>> Produtos registrados para o pedido " + idPedido + ": " + Arrays.toString(produtos));
        System.out.println(">>> Compra registrada! Tempo de entrega gerado: " + duracaoEntregaSegundos + " segundos.");
        
        return true;
    }

    @Override
    public int tempoEntrega(int idPedido) {
        System.out.println(">>> Verificando tempo de entrega para o pedido ID " + idPedido + "...");
        
        // 1. Valida se o pedido existe.
        if (!pedidos.containsKey(idPedido)) {
            System.err.println(">>> Falha: Pedido com ID " + idPedido + " não encontrado.");
            return -1; // Código de erro para "Pedido não encontrado"
        }
        
        Pedido pedido = pedidos.get(idPedido);

        // 2. Valida se a compra já foi feita (se o tempo de entrega já foi definido).
        if (pedido.getTempoConclusao() == 0) {
            System.out.println(">>> Pedido " + idPedido + " aguardando a compra dos produtos.");
            return -2; // Código de erro para "Aguardando compra"
        }

        // 3. Calcula o tempo restante.
        long tempoRestanteMs = pedido.getTempoConclusao() - System.currentTimeMillis();
        
        // Converte de milissegundos para segundos.
        int tempoRestanteSegundos = (int) (tempoRestanteMs / 1000);
        
        // Garante que não retornaremos um tempo negativo.
        int tempoFinal = Math.max(0, tempoRestanteSegundos);

        if (tempoFinal == 0) {
            System.out.println(">>> A entrega do pedido " + idPedido + " foi concluída.");
        } else {
            System.out.println(">>> Tempo restante para o pedido " + idPedido + ": " + tempoFinal + " segundos.");
        }
        
        return tempoFinal;
    }

    private static class Pedido {
        private final int id;
        private final String restaurante;
        private String[] produtos;
        private long tempoConclusao; // Armazena o timestamp de quando a entrega deve ser concluída.

        public Pedido(int id, String restaurante) {
            this.id = id;
            this.restaurante = restaurante;
        }

        public long getTempoConclusao() {
            return tempoConclusao;
        }

        public void setTempoConclusao(long tempoConclusao) {
            this.tempoConclusao = tempoConclusao;
        }
        
        public void setProdutos(String[] produtos) {
            this.produtos = produtos;
        }
    }
}
