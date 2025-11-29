package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClienteCallback extends Remote {
    void notificarPedidoEntregue(String[] pedido) throws RemoteException;
    
    void notificarEntregaMercado(String mensagem) throws RemoteException;
}