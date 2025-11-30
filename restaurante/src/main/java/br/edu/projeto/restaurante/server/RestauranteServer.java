package br.edu.projeto.restaurante.server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


import br.edu.projeto.interfaces.Restaurante;

public class RestauranteServer extends UnicastRemoteObject implements Restaurante {
    private final AtomicInteger comandaCounter = new AtomicInteger(0);
    private final Map<Integer, Map<String, Object>> comandas = new ConcurrentHashMap<>();

    public RestauranteServer() throws RemoteException {
        super();
        Cardapio.carregarCardapio("../menu_restaurante.csv");
}

    @Override
    public int novaComanda(String nome, int mesa) throws RemoteException {
        int comandaId = comandaCounter.incrementAndGet();
        comandas.put(comandaId, new ConcurrentHashMap<>());
        comandas.get(comandaId).put("cliente", nome);
        comandas.get(comandaId).put("mesa", mesa);
        comandas.get(comandaId).put("total", 0.0f);
        comandas.get(comandaId).put("pedidos", new ConcurrentHashMap<Integer, Integer>());
        System.out.println("Nova comanda " + comandaId + " criada para " + nome + " na mesa " + mesa);
        return comandaId;
    }

    @Override
    public String[] consultarCardapio() throws RemoteException {
        System.out.println("Um cliente está consultando o cardápio.");
        return Cardapio.consultarItens();
    }

    @Override
    public String fazerPedido(int comanda, String[] pedido) throws RemoteException {
        if (!comandas.containsKey(comanda)) {
            return "Erro: Comanda não encontrada.";
        }
        
        int foodId = Integer.parseInt(pedido[0]);
        int amount = Integer.parseInt(pedido[1]);

        Map<Integer, Integer> pedidosDaComanda = (Map<Integer, Integer>) comandas.get(comanda).get("pedidos");
        pedidosDaComanda.put(foodId, pedidosDaComanda.getOrDefault(foodId, 0) + amount);
        
        float valorPedido = Cardapio.getValor(foodId) * amount;
        float totalAtual = (float) comandas.get(comanda).get("total");
        comandas.get(comanda).put("total", totalAtual + valorPedido);

        System.out.println("Pedido recebido para comanda " + comanda + ": " + amount + "x (código " + foodId + ")");
        return "Pedido realizado com sucesso!";
    }

    @Override
    public float valorComanda(int comanda) throws RemoteException {
        if (!comandas.containsKey(comanda)) {
            System.err.println("Tentativa de consultar valor de comanda inexistente: " + comanda);
            return -1.0f;
        }
        System.out.println("Consulta de valor para comanda " + comanda);
        return (float) comandas.get(comanda).get("total");
    }

    @Override
    public boolean fecharComanda(int comanda) throws RemoteException {
        if (comandas.remove(comanda) != null) {
            System.out.println("Comanda " + comanda + " fechada com sucesso.");
            return true;
        }
        System.err.println("Tentativa de fechar comanda inexistente: " + comanda);
        return false;
    }
}