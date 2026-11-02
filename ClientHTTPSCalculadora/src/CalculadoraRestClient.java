import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class CalculadoraRestClient extends JFrame {
  private JTextField operador1TextField;
  private JTextField operador2TextField;
  private JComboBox<String> operacaoComboBox;
  private JTextField resultadoTextField;
  private JTextArea logTextArea;

  // Servidor PHP local (make server-php): mesma API REST do servidor remoto
  static final String URL_SERVIDOR = "http://localhost:8081";

  public CalculadoraRestClient() {
    super("Calculadora REST Client");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    add(panel);

    JPanel inputPanel = new JPanel();
    inputPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
    panel.add(inputPanel, BorderLayout.NORTH);

    inputPanel.add(new JLabel("Operador 1:"));
    operador1TextField = new JTextField(10);
    inputPanel.add(operador1TextField);

    inputPanel.add(new JLabel("Operador 2:"));
    operador2TextField = new JTextField(10);
    inputPanel.add(operador2TextField);

    inputPanel.add(new JLabel("Operação:"));
    operacaoComboBox = new JComboBox<>(new String[] { "soma", "subtração", "multiplicação", "divisão" });
    inputPanel.add(operacaoComboBox);

    JButton calcularButton = new JButton("Calcular");
    calcularButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        calcular();
      }
    });
    inputPanel.add(calcularButton);

    JPanel outputPanel = new JPanel();
    outputPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
    panel.add(outputPanel, BorderLayout.SOUTH);

    outputPanel.add(new JLabel("Resultado da Operação:"));
    resultadoTextField = new JTextField(10);
    resultadoTextField.setEditable(false);
    outputPanel.add(resultadoTextField);

    // Caixa de log rolável logo abaixo do resultado da operação
    logTextArea = new JTextArea(6, 30);
    logTextArea.setEditable(false);
    JScrollPane logScroll = new JScrollPane(logTextArea);
    logScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    logScroll.setPreferredSize(new Dimension(420, 110));
    panel.add(logScroll, BorderLayout.CENTER);

    pack();
    setLocationRelativeTo(null);
    setVisible(true);
  }

  // Prepara a requisição POST e executa fora da thread da UI com retry
  private void calcular() {
    String operador1 = operador1TextField.getText();
    String operador2 = operador2TextField.getText();

    // Converte o nome exibido no JComboBox para o caminho da operação na API
    // (a API usa somente letras sem acento: soma, subtracao, multiplicacao, divisao)
    String nomeOperacao = (String) operacaoComboBox.getSelectedItem();
    String operacao;
    switch (nomeOperacao) {
      case "subtração":
        operacao = "subtracao";
        break;
      case "multiplicação":
        operacao = "multiplicacao";
        break;
      case "divisão":
        operacao = "divisao";
        break;
      default:
        operacao = nomeOperacao;
    }

    // Requisição POST: /operation/<operacao>/<operador1>/<operador2>
    String urlStr = URL_SERVIDOR + "/operation/" + operacao + "/" + operador1 + "/" + operador2;

    registrar("Requisição POST " + urlStr);

    // A requisição e o backoff não podem travar a thread da interface (EDT),
    // então rodam em uma thread auxiliar e atualizam o resultado via invokeLater
    resultadoTextField.setText("Calculando...");
    new Thread(() -> {
      try {
        // Validação da URL antes do loop de retry (erro de URL não vale tentar de novo)
        URL url = new URI(urlStr).toURL();
        String resposta = chamarComBackoff(url);
        registrar("Resposta: " + resposta);
        SwingUtilities.invokeLater(() -> resultadoTextField.setText(resposta));
      } catch (MalformedURLException | URISyntaxException e) {
        System.out.println("URL inválida: " + urlStr);
        SwingUtilities.invokeLater(() -> resultadoTextField.setText("URL inválida"));
      } catch (IOException e) {
        System.out.println("Erro ao conectar ao servidor: " + e.getMessage());
        registrar("Erro ao conectar ao servidor: " + e.getMessage());
        // Mostra a causa no campo, ex.: "Conexão recusada" quando o servidor não está no ar
        SwingUtilities.invokeLater(() -> resultadoTextField.setText("Erro de conexão: " + e.getMessage()));
      } catch (Exception e) {
        // Qualquer outra exceção (ex.: resposta inesperada) não pode deixar a interface esperando
        e.printStackTrace();
        SwingUtilities.invokeLater(() -> resultadoTextField.setText("Erro inesperado"));
      }
    }).start();
  }

  // Adiciona uma linha na caixa de log (thread-safe, pode rodar fora da EDT)
  private void registrar(String linha) {
    SwingUtilities.invokeLater(() -> logTextArea.append(linha + "\n"));
  }

  // Exponential backoff: até 4 tentativas extras, com intervalo dobrando entre elas (1s, 2s, 4s, 8s)
  private String chamarComBackoff(URL url) throws IOException {
    final int MAX_RETRIES = 4;
    long intervaloBase = 1000;
    for (int tentativa = 0;; tentativa++) {
      try {
        return chamarRest(url);
      } catch (IOException e) {
        if (tentativa >= MAX_RETRIES) {
          throw e; // esgotou as retentativas
        }
        long intervalo = intervaloBase * (1L << tentativa);
        System.out.println("Falha (tentativa " + (tentativa + 1) + "): " + e.getMessage()
            + " - nova tentativa em " + intervalo + "ms");
        try {
          Thread.sleep(intervalo);
        } catch (InterruptedException ie) {
          throw new IOException("Requisição interrompida", ie);
        }
      }
    }
  }

  // Faz a requisição POST e lê a resposta do servidor
  private String chamarRest(URL url) throws IOException {
    // HttpURLConnection atende tanto http quanto https
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    // Timeouts: sem eles a conexão/leitura pode travar indefinidamente
    conn.setConnectTimeout(10_000);
    conn.setReadTimeout(10_000);

    try {
      // Tratamento da resposta: verifica o código HTTP antes de ler o corpo
      int codigo = conn.getResponseCode();
      if (codigo >= 500) {
        // Erro do servidor: pode ser temporário, então vale tentar de novo
        throw new IOException("Servidor indisponível (HTTP " + codigo + ")");
      }
      if (codigo != HttpURLConnection.HTTP_OK) {
        // Erro do cliente (ex.: 404 para operação inválida): repetir não muda o resultado
        InputStream streamErro = conn.getErrorStream();
        String mensagem = streamErro == null ? "" : lerTudo(streamErro);
        return "Erro HTTP " + codigo + ": " + mensagem;
      }

      // Lê o corpo JSON da resposta:
      // sucesso: {"result":14}
      // erro:    {"error":"Erro: divisão por zero."}
      String json = lerTudo(conn.getInputStream());
      if (json.contains("\"error\":")) {
        return json; // mensagem de erro da calculadora
      }
      // Extrai o valor do campo "result" do JSON
      return json.split("\"result\":")[1].split("[,}]")[0];
    } finally {
      conn.disconnect();
    }
  }

  // Lê todo o stream e fecha
  private static String lerTudo(InputStream stream) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
      return reader.lines().collect(Collectors.joining());
    }
  }

  public static void main(String[] args) {
    EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        new CalculadoraRestClient();
      }
    });
  }
}
