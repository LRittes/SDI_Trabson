package br.edu.projeto.coordenador;

import br.edu.projeto.interfaces.MercadoServidor;
import org.apache.zookeeper.ZooKeeper;
import javax.jws.WebService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@WebService(endpointInterface = "br.edu.projeto.interfaces.MercadoServidor")
public class MercadoImpl implements MercadoServidor {

    private ZooKeeper zk;

    // NOVO: Mapa para guardar quando cada pedido será entregue <ID_Pedido, Timestamp_Entrega>
    private static final Map<Integer, Long> entregas = new ConcurrentHashMap<>();
    
    public MercadoImpl() {
        try {
            // Conecta ao ZK apenas para ler a lista de filiais
            this.zk = new ZooKeeper("localhost:2181", 3000, event -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int cadastrarPedido(String restaurante) {
        System.out.println("Novo pedido iniciado para: " + restaurante);
        return (int) (System.currentTimeMillis() % 10000); // ID simples
    }

    // Classe auxiliar interna para guardar o histórico da Saga
    private static class ReservaEfetuada {
        String produto;
        String enderecoFilial; // IP:PORTA

        public ReservaEfetuada(String produto, String enderecoFilial) {
            this.produto = produto;
            this.enderecoFilial = enderecoFilial;
        }
    }

    @Override
    public boolean comprarProdutos(int idPedido, String[] produtos) {
        System.out.println("Coordenador: Iniciando Saga de Compra para o Pedido " + idPedido);
        
        List<ReservaEfetuada> logSaga = new ArrayList<>();
        boolean transacaoSucesso = true;

        try {
            List<String> filiaisNodes = zk.getChildren("/filiais", false);
            if (filiaisNodes.isEmpty()) throw new Exception("Sem filiais disponíveis");

            // --- FASE 1: TENTAR RESERVAR TUDO ---
            for (String produto : produtos) {
                String prodLimpo = produto.trim();
                
                // 1. Acha a melhor filial para esse produto (Lógica simplificada: pega a primeira que tiver estoque)
                String filialEscolhida = encontrarFilialComEstoque(prodLimpo, filiaisNodes);
                
                if (filialEscolhida == null) {
                    System.out.println("ERRO: Produto '" + prodLimpo + "' indisponível em toda a rede.");
                    throw new Exception("Produto Indisponível: " + prodLimpo);
                }

                // 2. Tenta Reservar
                boolean reservou = enviarComando(filialEscolhida, "RESERVAR:" + prodLimpo);
                
                if (reservou) {
                    // Sucesso: Adiciona no diário para caso precise cancelar depois
                    logSaga.add(new ReservaEfetuada(prodLimpo, filialEscolhida));
                } else {
                    throw new Exception("Falha ao reservar " + prodLimpo);
                }
            }

            System.out.println(">>> SAGA CONCLUÍDA: Todos os produtos reservados com sucesso!");
            
            // LÓGICA DE TEMPO: Define que a entrega chegará entre 5 e 10 segundos
            int segundosParaEntrega = 5 + new Random().nextInt(5);
            long horaEntrega = System.currentTimeMillis() + (segundosParaEntrega * 1000);
            
            entregas.put(idPedido, horaEntrega);
            System.out.println(">>> Entrega agendada para daqui a " + segundosParaEntrega + " segundos.");

            return true;

        } catch (Exception e) {
            // --- FASE 2: ROLLBACK (COMPENSAÇÃO) ---
            System.err.println(">>> FALHA NA SAGA: " + e.getMessage());
            System.out.println(">>> Iniciando Rollback dos itens já reservados...");
            transacaoSucesso = false;
            
            executarRollback(logSaga);
            
            return false;
        }
    }

    // Método auxiliar para buscar quem tem o produto (Simplificado do anterior)
    private String encontrarFilialComEstoque(String produto, List<String> filiais) {
        for (String node : filiais) {
            try {
                byte[] dados = zk.getData("/filiais/" + node, false, null);
                String endereco = new String(dados);
                
                // Verifica se tem estoque (CONSULTA)
                String resposta = enviarComandoSocket(endereco, "CONSULTAR:" + produto);
                // Resposta esperada: OK:PRECO:QTD
                if (resposta != null && resposta.startsWith("OK")) {
                    String[] parts = resposta.split(":");
                    double qtd = Double.parseDouble(parts[2]);
                    if (qtd > 0) return endereco; // Achamos uma filial com estoque!
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // Método auxiliar para disparar o Rollback
    private void executarRollback(List<ReservaEfetuada> logSaga) {
        for (ReservaEfetuada reserva : logSaga) {
            System.out.println("Compensando: Devolvendo " + reserva.produto + " para " + reserva.enderecoFilial);
            enviarComando(reserva.enderecoFilial, "CANCELAR:" + reserva.produto);
        }
    }

    // Método genérico para falar com o Socket
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

    @Override
    public int tempoEntrega(int idPedido) {
        if (!entregas.containsKey(idPedido)) {
            return -1; // Pedido não encontrado ou ainda não processado
        }

        long horaEntrega = entregas.get(idPedido);
        long agora = System.currentTimeMillis();
        
        int segundosRestantes = (int) ((horaEntrega - agora) / 1000);

        if (segundosRestantes <= 0) {
            return 0; // Entregue!
        }
        
        return segundosRestantes;
    }
}