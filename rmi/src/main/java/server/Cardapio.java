package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Cardapio {
    private static final Map<Integer, String[]> itens = new HashMap<>();

    public static void carregarCardapio(String arquivo) {
        try (BufferedReader br = new BufferedReader(new FileReader(arquivo))) {
            String linha;
            br.readLine();
            while ((linha = br.readLine()) != null) {
                String[] dados = linha.split(",");
                int codigo = Integer.parseInt(dados[0].trim());
                String nome = dados[1].trim();
                String valor = dados[2].trim();
                itens.put(codigo, new String[]{nome, valor});
            }
        } catch (IOException e) {
            System.err.println("Erro ao carregar o arquivo do cardápio: " + e.getMessage());
        }
    }
    
    public static String[] consultarItens() {
        return itens.entrySet().stream()
                .map(entry -> entry.getKey() + "," + entry.getValue()[0] + "," + entry.getValue()[1])
                .toArray(String[]::new);
    }

    public static String[] consultarItem(int codigo) {
        return itens.get(codigo);
    }
    
    public static float getValor(int codigo) {
        String[] item = itens.get(codigo);
        return item != null ? Float.parseFloat(item[1]) : 0.0f;
    }
}