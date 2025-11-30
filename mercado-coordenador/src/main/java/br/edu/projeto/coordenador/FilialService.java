package br.edu.projeto.coordenador;

import org.apache.zookeeper.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class FilialService {
    
    private final ZooKeeper zk;
    private final String meuNomeUnico; 
    
    private ServerSocket serverSocket;
    private boolean rodando = false;
    private String pathZkFilial; 

    private final Map<String, Double[]> estoque = new HashMap<>();

    public FilialService(ZooKeeper zk, String meuNomeUnico) {
        this.zk = zk;
        this.meuNomeUnico = meuNomeUnico;
        inicializarEstoque();
    }

    // ... (Métodos iniciar, parar, registrarNoZookeeper e processarRequisicao continuam IGUAIS) ...
    // ... (Copie os métodos iniciar, parar, etc. do código anterior, eles não mudam) ...

    public void iniciar() {
        if (rodando) return;
        rodando = true;

        new Thread(() -> {
            try {
                int porta = 4000 + new Random().nextInt(1000);
                serverSocket = new ServerSocket(porta);
                System.out.println(">>> [MODO FILIAL] Ouvindo na porta " + porta);

                registrarNoZookeeper(porta);

                while (rodando && !serverSocket.isClosed()) {
                    try {
                        Socket cliente = serverSocket.accept();
                        new Thread(() -> processarRequisicao(cliente)).start();
                    } catch (IOException e) {}
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void parar() {
        if (!rodando) return;
        rodando = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (pathZkFilial != null) zk.delete(pathZkFilial, -1);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // No método registrarNoZookeeper
    private void registrarNoZookeeper(int porta) throws Exception {
        if (zk.exists("/filiais", false) == null) {
            try { zk.create("/filiais", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); } 
            catch (KeeperException.NodeExistsException ignored) {}
        }

        // --- MUDANÇA AQUI ---
        // Tenta pegar o IP configurado via variável de ambiente, senão tenta descobrir
        String meuIp = System.getenv("MY_HOST_IP");
        if (meuIp == null || meuIp.isEmpty()) {
            // Fallback: Tenta pegar o IP da interface de rede (pode ser instável dependendo da rede)
            meuIp = java.net.InetAddress.getLocalHost().getHostAddress();
        }

        String dadosEndereco = meuIp + ":" + porta;
        // --------------------

        pathZkFilial = zk.create("/filiais/" + meuNomeUnico, dadosEndereco.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        System.out.println(">>> Registrado no ZK com endereço: " + dadosEndereco);
    }

    private void processarRequisicao(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requisicao = in.readLine();
            if (requisicao == null) return;
            
            String[] partes = requisicao.split(":");
            String comando = partes[0];
            String produto = partes.length > 1 ? partes[1].trim() : "";

            if (comando.equals("CONSULTAR")) {
                Double[] dados = getEstoque(produto);
                out.println(dados != null ? "OK:" + dados[0] + ":" + dados[1] : "ERRO:NAO_TEM");

            } else if (comando.equals("RESERVAR")) {
                boolean sucesso = baixarEstoque(produto);
                if (sucesso) {
                    out.println("OK:RESERVADO");
                    Double[] dados = getEstoque(produto);
                    System.out.println("$$$ [VENDIDO] O produto ID " + produto + " foi vendido aqui! Preço: R$ " + dados[0] + " $$$");
                } else {
                    out.println("ERRO:SEM_ESTOQUE");
                }

            } else if (comando.equals("CANCELAR")) {
                devolverEstoque(produto);
                out.println("OK:CANCELADO");
                System.out.println(">>> [CANCELADO] Compra do produto " + produto + " desfeita.");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // --- MÉTODOS DE CONTROLE DE ESTOQUE (Synchronized) ---
    private synchronized Double[] getEstoque(String p) { return estoque.get(p); }
    
    private synchronized boolean baixarEstoque(String p) {
        if (estoque.containsKey(p) && estoque.get(p)[1] >= 1) {
            estoque.get(p)[1]--; return true;
        }
        return false;
    }
    
    private synchronized void devolverEstoque(String p) {
        if (estoque.containsKey(p)) estoque.get(p)[1]++;
    }

    // --- NOVA LÓGICA DE INICIALIZAÇÃO COM CSV ---
    private void inicializarEstoque() {
        // O arquivo está na raiz, e nós rodamos de dentro do módulo, então subimos um nível (../)
        File arquivo = new File("../menu_restaurante.csv");
        List<String[]> candidatos = new ArrayList<>();
        

        System.out.println(">>> [Estoque] Carregando produtos de: " + arquivo.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(new FileReader(arquivo))) {
            String linha = br.readLine(); // Pula cabeçalho
            int lidos = 0;

            // 1. Lê os 10 primeiros itens do arquivo
            while ((linha = br.readLine()) != null && lidos < 10) {
                String[] dados = linha.split(",");
                // Formato CSV esperado: codigo,nome,valor
                candidatos.add(dados);
                lidos++;
            }
        } catch (IOException e) {
            System.err.println("ERRO CRÍTICO: Não foi possível ler o arquivo 'menu_restaurante.csv' na raiz.");
            // Fallback de emergência
            estoque.put("1", new Double[]{10.0, 50.0});
            return;
        }

        if (candidatos.isEmpty()) {
            System.err.println("ERRO: O arquivo de menu está vazio ou mal formatado.");
            return;
        }

        // 2. Embaralha a lista para dar aleatoriedade
        Collections.shuffle(candidatos);

        // 3. Pega os 5 primeiros da lista embaralhada
        int qtdParaPegar = Math.min(5, candidatos.size());
        Random r = new Random();

        for (int i = 0; i < qtdParaPegar; i++) {
            String[] item = candidatos.get(i);
            String id = item[0].trim();
            String nome = item[1].trim();
            double precoBase = Float.parseFloat(item[2].trim());
            
            // Adiciona uma pequena variação de preço para cada mercado competir (+- 10%)
            double variacao = 0.9 + (1.1 - 0.9) * r.nextDouble();
            double precoFinal = Math.round((precoBase * variacao) * 100.0) / 100.0;
            
            double quantidade = 1 + r.nextInt(2); // 1 a 2 unidades

            estoque.put(id, new Double[]{precoFinal, quantidade});
            
            System.out.println("   + Item Adicionado: " + nome + " (ID: " + id + ") -> R$ " + precoFinal + " | Quantidade: " + quantidade);
        }
        System.out.println(">>> Estoque inicializado com " + qtdParaPegar + " produtos aleatórios.");
    }
}