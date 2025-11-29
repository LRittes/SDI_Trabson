package br.edu.projeto.restaurante.client;

import java.util.HashMap;
import java.util.Map;

public class Mesa {
    final public int mesaId;
    final public int comandaId;
    public Map<Integer, Integer> pedidos = new HashMap<>();

    public Mesa(int mesaId, int comandaId) {
        this.mesaId = mesaId;
        this.comandaId = comandaId;
    }

    public int getMesaId() {
        return mesaId;
    }

    public void addPedido(int foodId, int amount) {
         this.pedidos.put(foodId, amount);
    }

    public void removePedido(int foodId, int amount) {
        int totalAmountFood = this.pedidos.get(foodId).intValue();
        if ( totalAmountFood > 1) {
            this.pedidos.remove(foodId);
            this.pedidos.put(foodId, totalAmountFood - amount);
            return;
        }
        this.pedidos.remove(foodId);
    }

    public int getComandaId() {
        return comandaId;
    }

    public Map<Integer, Integer> getPedidos() {
        return pedidos;
    }

    @Override
    public String toString() {
        return "Mesa [mesaId=" + mesaId + ", comandaId=" + comandaId + ", pedidos=" + pedidos + "]";
    }
}