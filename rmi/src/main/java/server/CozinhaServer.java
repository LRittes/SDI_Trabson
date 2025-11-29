package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import shared.Cozinha;

public class CozinhaServer extends UnicastRemoteObject implements Cozinha {
    private final AtomicInteger preparoCounter = new AtomicInteger(0);
    private final Map<Integer, Object[]> preparos = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public CozinhaServer() throws RemoteException {
        super();
    }

    @Override
    public int novoPreparo(int comanda, String[] pedido) throws RemoteException {
        int preparoId = preparoCounter.incrementAndGet();
        int tempoEstimado = random.nextInt(10) + 1;
        long tempoConclusao = System.currentTimeMillis() + (tempoEstimado * 1000L);
        
        preparos.put(preparoId, new Object[]{pedido, tempoConclusao});

        System.out.println("COZINHA: Novo preparo #" + preparoId + " para comanda " + comanda + ". Tempo: " + tempoEstimado + "s");
        return preparoId;
    }

    @Override
    public int tempoPreparo(int preparo) throws RemoteException {
        if (!preparos.containsKey(preparo)) {
            return -1; 
        }
        Object[] dadosPreparo = preparos.get(preparo);

        long tempoConclusao = (long) dadosPreparo[1];
        long tempoRestante = (tempoConclusao - System.currentTimeMillis()) / 1000;
        
        return (int) Math.max(0, tempoRestante);
    }

    @Override
    public String[] pegarPreparo(int preparo) throws RemoteException {
        if (tempoPreparo(preparo) <= 0) {
            System.out.println("COZINHA: Entregando preparo #" + preparo);
            Object[] dados = preparos.remove(preparo);
            return (String[]) dados[0];
        }
        System.out.println("COZINHA: Tentativa de pegar preparo #" + preparo + " antes do tempo.");
        return null; 
    }
}