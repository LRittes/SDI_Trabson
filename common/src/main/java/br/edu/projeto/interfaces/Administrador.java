package br.edu.projeto.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Administrador extends Remote {
    void encaminharPedido(int comanda, String[] pedido, ClienteCallback cliente) throws RemoteException;
    
    void fazerComprasNoMercado(String nomeRestaurante, String[] produtos, ClienteCallback cliente) throws RemoteException;
}