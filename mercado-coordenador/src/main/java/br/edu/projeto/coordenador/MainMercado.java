package br.edu.projeto.coordenador;

import org.apache.zookeeper.*;
import javax.xml.ws.Endpoint;
import java.util.Collections;
import java.util.List;

public class MainMercado implements Watcher {

    // Em MainMercado.java
    private static final String ZOOKEEPER_ADDRESS = System.getenv("ZOOKEEPER_HOST") != null ? System.getenv("ZOOKEEPER_HOST") : "localhost:2181";
    private static final String ELECTION_ROOT = "/eleicao";
    private static final String URL_WEBSERVICE = "http://localhost:8080/mercado";
    
    private ZooKeeper zk;
    private Endpoint endpointSOAP; // Ativo apenas se for LÍDER
    private FilialService filialService; // AGORA É ATIVO SEMPRE (Todo mundo tem estoque)
    
    private String meuNoNome; // Ex: n_0000000005

    public static void main(String[] args) {
        new MainMercado().iniciar();
    }

    public void iniciar() {
        try {
            System.out.println(">>> Iniciando Instância do Mercado...");
            boolean conectado = false;
            while (!conectado) {
                try {
                    this.zk = new ZooKeeper(ZOOKEEPER_ADDRESS, 1000, this);
                    // Bloqueia até conectar de verdade (verifica estado)
                    long inicio = System.currentTimeMillis();
                    while (!zk.getState().isConnected() && (System.currentTimeMillis() - inicio) < 5000) {
                        Thread.sleep(100);
                    }
                    
                    if (zk.getState().isConnected()) {
                        conectado = true;
                        System.out.println(">>> Conectado ao Zookeeper em " + ZOOKEEPER_ADDRESS);
                    } else {
                        System.err.println(">>> Zookeeper não respondeu. Tentando novamente em 2s...");
                        zk.close();
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    System.err.println(">>> Erro ao resolver Zookeeper (" + e.getMessage() + "). Tentando em 2s...");
                    Thread.sleep(2000);
                }
            }
            
            // 1. Configuração do Zookeeper (Pastas)
            if (zk.exists(ELECTION_ROOT, false) == null) {
                try { zk.create(ELECTION_ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); } 
                catch (KeeperException.NodeExistsException e) {}
            }

            // 2. Registra na Eleição
            String path = zk.create(ELECTION_ROOT + "/n_", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            this.meuNoNome = path.replace(ELECTION_ROOT + "/", "");
            System.out.println(">>> ID de Eleição: " + meuNoNome);

            // 3. INICIA O MODO FILIAL IMEDIATAMENTE (Para todos!)
            // Isso abre o Socket e registra em /filiais. 
            // Assim, mesmo o líder aparecerá na lista de quem tem estoque.
            this.filialService = new FilialService(zk, meuNoNome);
            this.filialService.iniciar();

            // 4. Verifica papel na hierarquia de coordenação
            verificarLideranca();
            
            synchronized (this) { wait(); }
            
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void verificarLideranca() throws KeeperException, InterruptedException {
        List<String> candidatos = zk.getChildren(ELECTION_ROOT, false);
        Collections.sort(candidatos);

        int meuIndex = candidatos.indexOf(meuNoNome);
        
        if (meuIndex == 0) {
            assumirLideranca();
        } else {
            String anterior = candidatos.get(meuIndex - 1);
            virarSeguidor(anterior);
        }
    }

    private void assumirLideranca() {
        System.out.println("\n>>> PAPEL: LÍDER (COORDENADOR + FILIAL) <<<");
        
        // MUDANÇA AQUI: Não paramos mais a filialService!
        // O Líder continua servindo estoque via Socket para si mesmo e para futuros líderes.

        // Inicia o WebService (Porta 8080) se ainda não estiver rodando
        if (endpointSOAP == null || !endpointSOAP.isPublished()) {
            try {
                System.out.println(">>> Subindo WebService na porta 8080...");
                endpointSOAP = Endpoint.publish(URL_WEBSERVICE, new MercadoImpl());
                System.out.println(">>> WebService ONLINE! (Acumulando função de Estoque)");
            } catch (Exception e) {
                System.err.println("ERRO CRÍTICO: Porta 8080 ocupada ou erro de rede: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    private void virarSeguidor(String noVigiado) throws KeeperException, InterruptedException {
        System.out.println("\n>>> PAPEL: SEGUIDOR (APENAS FILIAL) <<<");
        System.out.println(">>> Vigiando o nó anterior: " + noVigiado);

        // Se eu era líder e perdi o posto, desligo APENAS o SOAP.
        // O socket de estoque continua firme e forte.
        if (endpointSOAP != null && endpointSOAP.isPublished()) {
            endpointSOAP.stop();
            System.out.println(">>> WebService Parado (Continuo rodando como estoque).");
        }

        // Vigia o próximo da fila
        if (zk.exists(ELECTION_ROOT + "/" + noVigiado, true) == null) {
            verificarLideranca();
        }
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getType() == Event.EventType.NodeDeleted) {
            try { verificarLideranca(); } catch (Exception e) { e.printStackTrace(); }
        }
    }
}