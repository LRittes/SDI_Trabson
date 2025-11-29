package br.edu.projeto.filial;

import org.apache.zookeeper.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainFilial {
    // Simulação de banco de dados: Produto -> {Preço, Quantidade}
    private static final Map<String, Double[]> estoque = new HashMap<>();
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final String NOME_FILIAL = "Filial-" + new Random().nextInt(1000);
    private static int PORTA_SOCKET; // Porta aleatória para evitar conflito

    public static void main(String[] args) {
        try {
            // 1. Inicializa Estoque Aleatório
            inicializarEstoque();
            
            // 2. Define porta aleatória (entre 4000 e 5000)
            PORTA_SOCKET = 4000 + new Random().nextInt(1000);
            
            // 3. Registra no Zookeeper
            registrarNoZookeeper();
            
            // 4. Inicia Servidor de Socket para responder o Coordenador
            iniciarServidorSocket();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void registrarNoZookeeper() throws Exception {
        ZooKeeper zk = new ZooKeeper(ZOOKEEPER_ADDRESS, 3000, event -> {});
        
        // Cria nó pai se não existir
        if (zk.exists("/filiais", false) == null) {
            try {
                zk.create("/filiais", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (KeeperException.NodeExistsException e) { /* Ignora se já existe */ }
        }

        // Dados para o Coordenador saber como conectar: "IP:PORTA"
        String dadosEndereco = "localhost:" + PORTA_SOCKET;
        
        // Cria nó efêmero (se filial cair, o nó some)
        String path = zk.create("/filiais/" + NOME_FILIAL, dadosEndereco.getBytes(), 
                                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        
        System.out.println(">>> Filial registrada no Zookeeper: " + path + " (" + dadosEndereco + ")");
    }

    private static void iniciarServidorSocket() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORTA_SOCKET);
        System.out.println(">>> Filial ouvindo na porta " + PORTA_SOCKET);

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> processarRequisicao(socket)).start();
        }
    }

    // Mude a assinatura do processarRequisicao para esta lógica nova:
    private static void processarRequisicao(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requisicao = in.readLine();
            
            if (requisicao == null) return;

            // Protocolo: COMANDO:PRODUTO
            String[] partes = requisicao.split(":");
            String comando = partes[0];
            String produto = partes.length > 1 ? partes[1].trim() : "";

            if (comando.equals("CONSULTAR")) {
                Double[] dados = consultarEstoque(produto);
                if (dados != null) {
                    // Retorna: OK:PRECO:QUANTIDADE
                    out.println("OK:" + dados[0] + ":" + dados[1]);
                } else {
                    out.println("ERRO:PRODUTO_NAO_ENCONTRADO");
                }

            } else if (comando.equals("RESERVAR")) {
                boolean reservou = reservarProduto(produto);
                if (reservou) {
                    out.println("OK:RESERVADO");
                    System.out.println(">>> Reserva efetuada para: " + produto);
                } else {
                    out.println("ERRO:SEM_ESTOQUE");
                    System.out.println(">>> Falha na reserva: " + produto);
                }

            } else if (comando.equals("CANCELAR")) {
                devolverProduto(produto);
                out.println("OK:CANCELADO");
                System.out.println(">>> Compensação (Rollback): Produto " + produto + " devolvido.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Métodos Auxiliares Thread-Safe (Synchronized é vital aqui) ---

    private static synchronized Double[] consultarEstoque(String produto) {
        if (estoque.containsKey(produto)) {
            return estoque.get(produto);
        }
        return null;
    }

    private static synchronized boolean reservarProduto(String produto) {
        if (estoque.containsKey(produto)) {
            Double[] dados = estoque.get(produto);
            double qtdAtual = dados[1];
            
            if (qtdAtual >= 1.0) { // Assume que cada pedido consome 1 unidade
                dados[1] = qtdAtual - 1.0;
                estoque.put(produto, dados);
                return true;
            }
        }
        return false;
    }

    private static synchronized void devolverProduto(String produto) {
        if (estoque.containsKey(produto)) {
            Double[] dados = estoque.get(produto);
            dados[1] = dados[1] + 1.0; // Devolve 1 unidade
            estoque.put(produto, dados);
        }
    }
    

    private static void inicializarEstoque() {
        // Preenche com produtos do seu CSV original ou aleatórios
        Random r = new Random();
        // Exemplo: Produto 1 (Arroz), Produto 2 (Feijão)
        estoque.put("1", new Double[]{10.0 + r.nextInt(5), 100.0}); 
        estoque.put("2", new Double[]{5.0 + r.nextInt(3), 50.0});
        estoque.put("3", new Double[]{20.0, 10.0}); // Picanha
    }
}