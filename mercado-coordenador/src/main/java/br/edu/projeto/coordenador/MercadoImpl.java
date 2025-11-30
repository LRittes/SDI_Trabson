package br.edu.projeto.coordenador;

import br.edu.projeto.interfaces.MercadoServidor;
import org.apache.zookeeper.ZooKeeper;
import javax.jws.WebService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@WebService(endpointInterface = "br.edu.projeto.interfaces.MercadoServidor")
public class MercadoImpl implements MercadoServidor {

    private ZooKeeper zk;
    // Mapa para guardar entregas: <ID_Pedido, Timestamp_Entrega>
    private static final Map<Integer, Long> entregas = new ConcurrentHashMap<>();

    // Classe auxiliar para guardar a melhor oferta encontrada
    private static class MelhorOferta {
        String produto;
        String enderecoFilial; // IP:PORTA
        double preco;

        public MelhorOferta(String produto, String enderecoFilial, double preco) {
            this.produto = produto;
            this.enderecoFilial = enderecoFilial;
            this.preco = preco;
        }
    }

    public MercadoImpl() {
        try {
            // Lógica para pegar o endereço correto (Docker ou Local)
            String zkHost = System.getenv("ZOOKEEPER_HOST") != null ? System.getenv("ZOOKEEPER_HOST") : "localhost:2181";
            
            // Usa o endereço dinâmico
            this.zk = new ZooKeeper(zkHost, 1500, event -> {});
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int cadastrarPedido(String restaurante) {
        System.out.println(">>> Novo pedido iniciado para: " + restaurante);
        return (int) (System.currentTimeMillis() % 10000);
    }

    @Override
    public boolean comprarProdutos(int idPedido, String[] produtos) {
        System.out.println("\n=== INICIANDO COTAÇÃO PARA PEDIDO " + idPedido + " ===");
        
        // Lista para guardar onde vamos comprar cada produto (Plano de Compra)
        List<MelhorOferta> carrinhoDeCompras = new ArrayList<>();
        // Lista para log de rollback (caso algo dê errado na hora de pagar)
        List<MelhorOferta> reservasEfetuadas = new ArrayList<>();

        try {
            List<String> filiaisNodes = zk.getChildren("/filiais", false);
            if (filiaisNodes.isEmpty()) throw new Exception("Sem mercados disponíveis na rede.");

            // --- FASE 1: COTAÇÃO (Descobrir o menor preço) ---
            for (String produto : produtos) {
                String prodLimpo = produto.trim();
                
                // Busca o menor preço em TODAS as filiais
                MelhorOferta ofertaVencedora = encontrarMenorPreco(prodLimpo, filiaisNodes);
                
                if (ofertaVencedora == null) {
                    throw new Exception("Produto Indisponível na rede: " + prodLimpo);
                }

                System.out.println("   -> Vencedor para '" + prodLimpo + "': " + ofertaVencedora.enderecoFilial + " (R$ " + ofertaVencedora.preco + ")");
                carrinhoDeCompras.add(ofertaVencedora);
            }

            System.out.println(">>> Cotação finalizada. Iniciando compras...");

            // --- FASE 2: EXECUÇÃO (Reservar nos vencedores) ---
            for (MelhorOferta item : carrinhoDeCompras) {
                boolean reservou = enviarComando(item.enderecoFilial, "RESERVAR:" + item.produto);
                
                if (reservou) {
                    reservasEfetuadas.add(item);
                } else {
                    // Se falhar na hora H (ex: alguém comprou na frente), aborta tudo
                    throw new Exception("Falha ao reservar " + item.produto + " no mercado " + item.enderecoFilial);
                }
            }

            // --- FASE 3: SUCESSO ---
            System.out.println(">>> SAGA CONCLUÍDA: Compra efetuada com sucesso!");
            
            // Agenda a entrega
            int segundosParaEntrega = 5 + new Random().nextInt(10);
            long horaEntrega = System.currentTimeMillis() + (segundosParaEntrega * 1000L);
            entregas.put(idPedido, horaEntrega);
            
            return true;

        } catch (Exception e) {
            // --- FASE 4: ROLLBACK (Se der erro, devolve o que já comprou) ---
            System.err.println(">>> FALHA NA TRANSAÇÃO: " + e.getMessage());
            System.out.println(">>> Iniciando Rollback...");
            
            for (MelhorOferta item : reservasEfetuadas) {
                System.out.println("   Compensando: Devolvendo " + item.produto + " para " + item.enderecoFilial);
                enviarComando(item.enderecoFilial, "CANCELAR:" + item.produto);
            }
            
            return false;
        }
    }

    private MelhorOferta encontrarMenorPreco(String produto, List<String> filiais) {
        MelhorOferta melhor = null;

        for (String node : filiais) {
            try {
                // Pega IP:PORTA do Zookeeper
                byte[] dados = zk.getData("/filiais/" + node, false, null);
                String endereco = new String(dados);
                
                // Consulta: CONSULTAR:1
                String resposta = enviarComandoSocket(endereco, "CONSULTAR:" + produto);
                
                // Resposta esperada: OK:PRECO:QTD (Ex: OK:10.5:50)
                if (resposta != null && resposta.startsWith("OK")) {
                    String[] parts = resposta.split(":");
                    double preco = Double.parseDouble(parts[1]);
                    double qtd = Double.parseDouble(parts[2]);

                    if (qtd > 0) {
                        // Lógica do menor preço
                        if (melhor == null || preco < melhor.preco) {
                            melhor = new MelhorOferta(produto, endereco, preco);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Se uma filial falhar, apenas ignora e tenta a próxima
            }
        }
        return melhor;
    }

    @Override
    public int tempoEntrega(int idPedido) {
        if (!entregas.containsKey(idPedido)) return -1;
        long horaEntrega = entregas.get(idPedido);
        long agora = System.currentTimeMillis();
        int segundosRestantes = (int) ((horaEntrega - agora) / 1000);
        return Math.max(0, segundosRestantes);
    }

    // --- Métodos de Socket ---
    private boolean enviarComando(String endereco, String comando) {
        String resposta = enviarComandoSocket(endereco, comando);
        return resposta != null && resposta.startsWith("OK");
    }

    private String enviarComandoSocket(String endereco, String comando) {
        try {
            String[] parts = endereco.split(":");
            try (Socket s = new Socket(parts[0], Integer.parseInt(parts[1]));
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                
                out.println(comando);
                return in.readLine();
            }
        } catch (Exception e) {
            return null;
        }
    }
}