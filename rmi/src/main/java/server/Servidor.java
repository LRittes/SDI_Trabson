package server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Servidor {
    public static void main(String[] args) {
        try {
            RestauranteServer restaurante = new RestauranteServer();
            CozinhaServer cozinha = new CozinhaServer();
            AdministradorServer administrador = new AdministradorServer(restaurante, cozinha);

            Registry registry = LocateRegistry.createRegistry(3099);
            
            registry.bind("RestauranteService", restaurante);
            registry.bind("CozinhaService", cozinha);
            registry.bind("AdministradorService", administrador);


            System.out.println("Servidor do Restaurante e Administrador estão prontos.");

            Object lock = new Object();
            synchronized (lock) {
                lock.wait();
            }
        } catch (Exception e) {
            System.err.println("Exceção no servidor: " + e.toString());
            e.printStackTrace();
        }
    }
}