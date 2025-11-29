package br.edu.projeto.coordenador;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import javax.xml.ws.Endpoint;
import java.util.Collections;
import java.util.List;

public class MainCoordenador implements Watcher {

    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final String ELECTION_ROOT = "/eleicao"; // Pasta raiz da eleição
    private static final String URL_WEBSERVICE = "http://localhost:8080/mercado";
    
    private ZooKeeper zk;
    private Endpoint endpoint;
    private String meuNoNome; // Ex: n_0000000005

    public static void main(String[] args) {
        new MainCoordenador().iniciar();
    }

    public void iniciar() {
        try {
            // 1. Conecta ao Zookeeper
            this.zk = new ZooKeeper(ZOOKEEPER_ADDRESS, 3000, this);
            
            // 2. Garante que a pasta pai "/eleicao" existe
            verificarRaizEleicao();

            // 3. Registra-se na fila (Pega a senha)
            registrarNaEleicao();

            // 4. Verifica se é minha vez
            verificarLideranca();
            
            // Mantém a main viva
            synchronized (this) { wait(); }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void verificarRaizEleicao() throws KeeperException, InterruptedException {
        if (zk.exists(ELECTION_ROOT, false) == null) {
            try {
                zk.create(ELECTION_ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (KeeperException.NodeExistsException e) {
                // Ignora se alguém criou milissegundos antes de mim
            }
        }
    }

    private void registrarNaEleicao() throws KeeperException, InterruptedException {
        // Cria nó EPHEMERAL_SEQUENTIAL (Ex: /eleicao/n_000000001)
        String prefixo = ELECTION_ROOT + "/n_";
        String caminhoCompleto = zk.create(prefixo, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        
        // Guarda apenas o nome curto (n_000000001) para facilitar comparações
        this.meuNoNome = caminhoCompleto.replace(ELECTION_ROOT + "/", "");
        
        System.out.println(">>> Registrado na eleição com ID: " + meuNoNome);
    }

    private void verificarLideranca() throws KeeperException, InterruptedException {
        // 1. Pega lista de todos os candidatos
        List<String> candidatos = zk.getChildren(ELECTION_ROOT, false);
        
        // 2. Ordena a lista (O Zookeeper não garante ordem no retorno)
        Collections.sort(candidatos);

        // 3. Descobre minha posição na fila
        int meuIndex = candidatos.indexOf(meuNoNome);
        
        if (meuIndex == 0) {
            // SOU O PRIMEIRO DA FILA -> LÍDER
            assumirLideranca();
        } else {
            // NÃO SOU O PRIMEIRO -> VIGIO O ANTERIOR
            String nomeDoNoAnterior = candidatos.get(meuIndex - 1);
            virarSeguidor(nomeDoNoAnterior);
        }
    }

    private void assumirLideranca() {
        // Se já sou líder, não faz nada
        if (endpoint != null && endpoint.isPublished()) return;

        System.out.println("\n>>> EU SOU O LÍDER (" + meuNoNome + ")! <<<");
        System.out.println(">>> Iniciando WebService na porta 8080...");
        
        try {
            endpoint = Endpoint.publish(URL_WEBSERVICE, new MercadoImpl());
            System.out.println(">>> Serviço SOAP publicado com sucesso!");
        } catch (Exception e) {
            System.err.println("Erro crítico ao subir serviço: " + e.getMessage());
            System.exit(1);
        }
    }

    private void virarSeguidor(String noAlvo) throws KeeperException, InterruptedException {
        System.out.println(">>> Sou Seguidor. Vigiando o nó: " + noAlvo);
        
        // Coloca o Watcher APENAS no nó anterior
        Stat stat = zk.exists(ELECTION_ROOT + "/" + noAlvo, true);
        
        if (stat == null) {
            // O nó que eu ia vigiar morreu no meio do processo!
            // Verifica a liderança de novo imediatamente
            verificarLideranca();
        }
    }

    @Override
    public void process(WatchedEvent event) {
        // Se o nó que eu estava vigiando foi deletado...
        if (event.getType() == Event.EventType.NodeDeleted) {
            try {
                // ... verifico a situação da fila novamente
                verificarLideranca();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}