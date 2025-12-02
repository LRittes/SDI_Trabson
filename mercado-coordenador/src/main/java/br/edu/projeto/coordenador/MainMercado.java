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
                    this.zk = new ZooKeeper(ZOOKEEPER_ADDRESS, 10000, this);
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
        
        // 1. URL DE BIND (Para o Java abrir a porta)
        // Usamos 0.0.0.0 para garantir que o Docker consiga rotear o tráfego para cá
        String urlBind = "http://0.0.0.0:8080/mercado";

        // 2. URL PÚBLICA (Para o Cliente encontrar)
        // Usamos o nome do host/IP configurado para o Zookeeper
        String meuHost = System.getenv("MY_HOST_IP");
        if (meuHost == null || meuHost.isEmpty()) {
            meuHost = "localhost"; 
        }
        String urlPublica = "http://" + meuHost + ":8080/mercado?wsdl";

        // Se o SOAP já não estiver rodando, inicio.
        if (endpointSOAP == null || !endpointSOAP.isPublished()) {
            try {
                System.out.println(">>> Subindo WebService (Bind) em: " + urlBind);
                
                // MUDANÇA CRÍTICA: Publica no 0.0.0.0
                endpointSOAP = Endpoint.publish(urlBind, new MercadoImpl());
                
                System.out.println(">>> WebService ONLINE!");
                
                // --- Publica a URL PÚBLICA (mercado1, mercado2...) no Zookeeper ---
                if (zk.exists("/servico-soap", false) != null) {
                    zk.delete("/servico-soap", -1);
                }
                
                zk.create("/servico-soap", urlPublica.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                System.out.println(">>> URL registrada no ZK para clientes: " + urlPublica);
                
            } catch (Exception e) {
                System.err.println("ERRO CRÍTICO: " + e.getMessage());
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
        // Lógica existente de nó deletado
        if (event.getType() == Event.EventType.NodeDeleted) {
            try { verificarLideranca(); } catch (Exception e) { e.printStackTrace(); }
        }
        
        // --- NOVA LÓGICA: SESSÃO EXPIRADA ---
        // Se a sessão morreu, o objeto 'zk' atual é inútil. Precisamos reiniciar tudo.
        if (event.getState() == Event.KeeperState.Expired) {
            System.err.println("!!! SESSÃO ZOOKEEPER EXPIRADA !!!");
            System.out.println(">>> Reiniciando conexão e eleição...");
            
            try {
                // Fecha o antigo se der
                try { zk.close(); } catch (Exception e) {}
                
                // Se eu era líder, derrubo o serviço para evitar "Split Brain"
                if (endpointSOAP != null && endpointSOAP.isPublished()) {
                    endpointSOAP.stop();
                }
                
                // Reinicia o processo do zero (reconecta, registra, vota)
                iniciar(); 
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}