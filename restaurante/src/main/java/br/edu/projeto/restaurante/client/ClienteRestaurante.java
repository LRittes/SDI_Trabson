package br.edu.projeto.restaurante.client;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.Scanner;

import br.edu.projeto.interfaces.*;

public class ClienteRestaurante extends UnicastRemoteObject implements ClienteCallback {

    protected ClienteRestaurante() throws RemoteException {
        super();
    }

    @Override
    public void notificarPedidoEntregue(String[] pedido) throws RemoteException {
        System.out.println("\n\n--- NOTIFICAÇÃO DA COZINHA ---");
        System.out.println("Seu pedido (Cód: " + pedido[0] + ", Qtd: " + pedido[1] + ") foi entregue na sua mesa!");
        System.out.println("----------------------------");
        System.out.print("Escolha uma opção: "); 
    }

    @Override
    public void notificarEntregaMercado(String mensagem) throws RemoteException {
        System.out.println("\n\n--- NOTIFICAÇÃO DO MERCADO ---");
        System.out.println(mensagem);
        System.out.println("----------------------------");
        System.out.print("Escolha uma opção: "); 
    }

    public static void main(String[] args) {
        try {
            String hostServidor = System.getenv("SERVER_HOST") != null ? System.getenv("SERVER_HOST") : "localhost";
            System.out.println(">>> Conectando ao servidor RMI em: " + hostServidor);
            Registry registry = LocateRegistry.getRegistry(hostServidor, 3099);
            
            Restaurante restaurante = (Restaurante) registry.lookup("RestauranteService");
            Administrador administrador = (Administrador) registry.lookup("AdministradorService");
            
            Scanner scanner = new Scanner(System.in);
            ClienteRestaurante clienteCallback = new ClienteRestaurante();

            while (true) {
                System.out.println("\n====== BEM-VINDO AO RESTAURANTE LRJP ======");
                System.out.println("1. Entrar no Modo Cliente");
                System.out.println("2. Entrar no Modo Administrador");
                System.out.println("3. Sair");
                System.out.print("Escolha uma opção: ");
                
                String input = scanner.nextLine();
                int modo;
                try {
                    modo = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.out.println("Opção inválida. Por favor, digite um número.");
                    continue;
                }
                
                if (modo == 1) {
                    iniciarModoCliente(scanner, restaurante, administrador, clienteCallback);
                } else if (modo == 2) {
                    iniciarModoAdmin(scanner, administrador, clienteCallback);
                } else if (modo == 3) {
                    System.out.println("Saindo do sistema...");
                    UnicastRemoteObject.unexportObject(clienteCallback, true);
                    System.exit(0);
                } else {
                    System.out.println("Opção inválida.");
                }
            }

        } catch (Exception e) {
            System.err.println("Exceção no cliente: " + e.toString());
            e.printStackTrace();
        }
    }
    
    public static void iniciarModoCliente(Scanner scanner, Restaurante restaurante, Administrador administrador, ClienteCallback clienteCallback) throws Exception {
        System.out.print("\nDigite seu nome: ");
        String nomeCliente = scanner.nextLine();
        System.out.print("Digite o número da sua mesa: ");
        int numeroMesa = Integer.parseInt(scanner.nextLine());

        int comandaId = restaurante.novaComanda(nomeCliente, numeroMesa);
        Mesa minhaMesa = new Mesa(numeroMesa, comandaId);
        System.out.println("Comanda " + comandaId + " aberta para " + nomeCliente + " na mesa " + numeroMesa);
        
        boolean noModoCliente = true;
        while (noModoCliente) {
            System.out.println("\n========== MENU CLIENTE ==========");
            System.out.println("1. Consultar Cardápio");
            System.out.println("2. Adicionar Pedido a Comanda");
            System.out.println("3. Ver Comanda / Finalizar Pedido");
            System.out.println("4. Verificar Conta");
            System.out.println("5. Pagar e Sair (Voltar ao menu principal)");
            System.out.print("Escolha uma opção: ");

            int escolha = Integer.parseInt(scanner.nextLine());

            switch (escolha) {
                case 1:
                    System.out.println("\n--- Cardápio ---");
                    String[] cardapio = restaurante.consultarCardapio();
                    for (String item : cardapio) {
                        String[] detalhes = item.split(",");
                        System.out.printf("Cód: %s | Prato: %-20s | Valor: R$ %s\n", detalhes[0], detalhes[1], detalhes[2]);
                    }
                    System.out.println("----------------");
                    break;
                case 2:
                    System.out.print("Digite o código do produto: ");
                    int codProduto = Integer.parseInt(scanner.nextLine());
                    System.out.print("Digite a quantidade: ");
                    int quantidade = Integer.parseInt(scanner.nextLine());
                    minhaMesa.addPedido(codProduto, quantidade);
                    System.out.println("=> Item adicionado a sua comanda!");
                    break;
                case 3:
                    verEFinalizarCarrinho(scanner, minhaMesa, administrador, clienteCallback);
                    break;
                case 4:
                    float total = restaurante.valorComanda(comandaId);
                    System.out.printf("\nO valor total da sua conta (pedidos já enviados) é: R$ %.2f\n", total);
                    break;
                case 5:
                    if (!minhaMesa.getPedidos().isEmpty()) {
                        System.out.println("AVISO: Você tem itens na comanda que não foram enviados. Eles serão perdidos.");
                    }
                    restaurante.fecharComanda(comandaId);
                    System.out.println("Comanda fechada. Voltando ao menu principal.");
                    noModoCliente = false;
                    break;
                default:
                    System.out.println("Opção inválida.");
            }
        }
    }

    private static void verEFinalizarCarrinho(Scanner scanner, Mesa mesa, Administrador administrador, ClienteCallback clienteCallback) throws RemoteException {
        if (mesa.getPedidos().isEmpty()) {
            System.out.println("\nSua comanda está vazio.");
            return;
        }

        boolean noCarrinho = true;
        while(noCarrinho) {
            System.out.println("\n--- Sua Comanda de Pedidos ---");
            for (Map.Entry<Integer, Integer> entry : mesa.getPedidos().entrySet()) {
                System.out.println("Cód: " + entry.getKey() + " | Quantidade: " + entry.getValue());
            }
            System.out.println("-------------------------------");

            System.out.println("\nO que deseja fazer?");
            System.out.println("1. Enviar todos os pedidos para a cozinha (Finalizar)");
            System.out.println("2. Remover um item da comanda");
            System.out.println("3. Voltar ao menu de cliente");
            System.out.print("Escolha uma opção: ");
            int escolhaCarrinho = Integer.parseInt(scanner.nextLine());

            switch (escolhaCarrinho) {
                case 1:
                    System.out.println("\nEnviando pedidos para o administrador...");
                    for (Map.Entry<Integer, Integer> entry : mesa.getPedidos().entrySet()) {
                        String[] pedido = {String.valueOf(entry.getKey()), String.valueOf(entry.getValue())};
                        administrador.encaminharPedido(mesa.getComandaId(), pedido, clienteCallback);
                    }
                    mesa.getPedidos().clear();
                    System.out.println("Pedidos encaminhados! Você será notificado quando a entrega for feita.");
                    noCarrinho = false;
                    break;
                case 2:
                    System.out.print("Digite o código do produto para remover: ");
                    int codRemover = Integer.parseInt(scanner.nextLine());
                    if (mesa.getPedidos().containsKey(codRemover)) {
                        mesa.removePedido(codRemover, 1);
                        System.out.println("Item removido da comanda.");
                    } else {
                        System.out.println("Este item não está na sua comanda.");
                    }
                    if (mesa.getPedidos().isEmpty()) {
                        System.out.println("Sua comanda agora está vazio.");
                        noCarrinho = false;
                    }
                    break;
                case 3:
                    noCarrinho = false;
                    break;
                default:
                    System.out.println("Opção inválida.");
            }
        }
    }

    public static void iniciarModoAdmin(Scanner scanner, Administrador administrador, ClienteCallback clienteCallback) throws RemoteException {
        boolean noModoAdmin = true;
        while (noModoAdmin) {
            System.out.println("\n--- MODO ADMINISTRADOR ---");
            System.out.println("1. Fazer Compras no Mercado");
            System.out.println("2. Voltar ao Menu Principal");
            System.out.print("Escolha uma opção: ");

            int escolha = Integer.parseInt(scanner.nextLine());
            
            switch (escolha) {
                case 1:
                    System.out.print("Digite os produtos para comprar (separados por vírgula): ");
                    String produtosInput = scanner.nextLine();
                    String[] produtos = produtosInput.split(",");
                    
                    for (int i = 0; i < produtos.length; i++) {
                        produtos[i] = produtos[i].trim();
                    }
                    
                    System.out.println("\nEnviando requisição de compra... O monitoramento será iniciado.");
                    administrador.fazerComprasNoMercado("Restaurante LRJP Principal", produtos, clienteCallback);
                    
                    System.out.println("Requisição enviada! Você será notificado quando a entrega chegar.");
                    break;
                case 2:
                    noModoAdmin = false;
                    break;
                default:
                    System.out.println("Opção inválida.");
            }
        }
    }
}