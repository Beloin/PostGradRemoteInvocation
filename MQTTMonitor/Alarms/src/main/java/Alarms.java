import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

// Serviço Alarms com interface gráfica (os 0,5 pontos adicionais da atividade):
// - eventos do CAT (caldeira/cat/evento) viram alarmes publicados em caldeira/alarme
// - o painel mostra: log de alarmes em caixa rolável, gráfico da média das
//   temperaturas (caldeira/cat/media) e o painel "Simulador de Sensores",
//   que configura o projeto Python sensorSimulator por chamadas remotas
//   (HTTP + JSON — Python não serve RMI nativo do Java)
// - sem display (headless) o serviço roda em modo console, só imprimindo
public class Alarms {

  static final String BROKER = "tcp://localhost:1883";
  static final String TOPICO_EVENTO = "caldeira/cat/evento";
  static final String TOPICO_ALARMES = "caldeira/alarme";
  static final String TOPICO_MEDIA = "caldeira/cat/media";
  // Serviço do simulador (sensorSimulator/simulador.py): configuração em memória
  static final String URL_SIMULADOR = "http://localhost:8090";

  static MqttClient client;
  static boolean modoGrafico;

  // Componentes do painel
  static JTextArea logAlarmes;
  static GraficoMedia grafico;
  static JPanel painelSimulador;
  static JTextField campoBase, campoMinimo, campoMaximo, campoIntervalo, campoSensores;
  static JButton botaoIniciar;
  static JButton botaoAtualizar;
  static JLabel statusSimulador;
  // Última configuração conhecida do simulador (para habilitar o botão Atualizar)
  static Map<String, Object> configSalva;
  static boolean simuladorDisponivel;
  static boolean simulando;

  public static void main(String[] args) {
    try {
      // Sem display o serviço roda em console (útil para servidores/testes)
      modoGrafico = !GraphicsEnvironment.isHeadless();
      client = new MqttClient(BROKER, "servico-alarms", new MemoryPersistence());
      MqttConnectOptions opcoes = new MqttConnectOptions();
      opcoes.setAutomaticReconnect(true);
      opcoes.setCleanSession(true);
      client.connect(opcoes);

      // Inscrição: cada evento do CAT vira um alarme publicado
      client.subscribe(TOPICO_EVENTO, (topico, mensagem) -> dispararAlarme(mensagem));
      // Inscrição: a média das temperaturas alimenta o gráfico do painel
      client.subscribe(TOPICO_MEDIA, (topico, mensagem) -> tratarMedia(mensagem));
      // Inscrição: cada alarme publicado vira uma linha no log do painel
      client.subscribe(TOPICO_ALARMES, (topico, mensagem) -> registrarAlarme(mensagem));

      if (modoGrafico) {
        SwingUtilities.invokeLater(() -> montarInterface());
        System.out.println("Servidor no ar (Alarms GUI)");
      } else {
        System.out.println("Servidor no ar (Alarms console — sem display)");
      }
    } catch (MqttException e) {
      e.printStackTrace();
    }
  }

  // Cada evento do CAT vira um alarme publicado em caldeira/alarme
  static void dispararAlarme(MqttMessage mensagem) {
    String evento = new String(mensagem.getPayload());
    String alarme = comporAlarme(evento);
    try {
      client.publish(TOPICO_ALARMES, new MqttMessage(alarme.getBytes(StandardCharsets.UTF_8)));
    } catch (MqttException e) {
      e.printStackTrace();
    }
    if (!modoGrafico) {
      // modo console usa a mesma linha legível do painel
      System.out.println("[ALARM] " + linhaAlarme(alarme));
    }
  }

  // Acrescenta a hora do alarme ao evento: {"tipo":...,"hora":1717171717}
  static String comporAlarme(String evento) {
    long agora = System.currentTimeMillis();
    Map<String, Object> json = Json.fromJson(evento);
    if (json == null) {
      // evento fora do padrão JSON: embute o texto original
      json = new LinkedHashMap<>();
      json.put("evento", evento);
    }
    json.put("hora", agora);
    return Json.toJson(json);
  }

  // Cada alarme publicado vira uma linha legível no log do painel
  static void registrarAlarme(MqttMessage mensagem) {
    if (!modoGrafico) {
      return;
    }
    String linha = linhaAlarme(new String(mensagem.getPayload()));
    SwingUtilities.invokeLater(() -> {
      logAlarmes.append(linha + "\n");
      logAlarmes.setCaretPosition(logAlarmes.getDocument().getLength());
    });
  }

  // Formata um alarme (JSON com "hora" em ms desde a época) como linha legível
  static String linhaAlarme(String payload) {
    Map<String, Object> alarme = Json.fromJson(payload);
    if (alarme == null) {
      return payload; // payload fora do padrão: mostra cru
    }
    // "hora" em milissegundos vira data e hora legíveis
    String hora = dataHora(Json.numero(alarme, "hora"));
    String tipo = String.valueOf(alarme.getOrDefault("tipo", "evento")).replace("-", " ").toUpperCase();
    Double media = Json.numero(alarme, "media");
    Double mediaAnterior = Json.numero(alarme, "mediaAnterior");
    Double mediaAtual = Json.numero(alarme, "mediaAtual");
    if (mediaAnterior != null && mediaAtual != null) {
      // aumento-repentina: mostra a média antes -> depois
      return String.format("[%s] %s — média %.1f°C -> %.1f°C", hora, tipo, mediaAnterior, mediaAtual);
    }
    if (media != null) {
      return String.format("[%s] %s — média %.1f°C", hora, tipo, media);
    }
    return "[" + hora + "] " + Json.toJson(alarme);
  }

  // Converte milissegundos desde a época em "dd/MM/yyyy HH:mm:ss"
  static String dataHora(Double timestampMillis) {
    long valor = timestampMillis != null ? timestampMillis.longValue() : System.currentTimeMillis();
    return Instant.ofEpochMilli(valor).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
  }

  // A média recebida alimenta o gráfico
  static void tratarMedia(MqttMessage mensagem) {
    if (!modoGrafico) {
      return;
    }
    Map<String, Object> dados = Json.fromJson(new String(mensagem.getPayload()));
    if (dados == null) {
      return;
    }
    Double media = Json.numero(dados, "media");
    if (media == null) {
      return;
    }
    Double ts = Json.numero(dados, "timestamp");
    long timestamp = ts != null ? ts.longValue() : System.currentTimeMillis();
    SwingUtilities.invokeLater(() -> grafico.adicionar(timestamp, media));
  }

  // Monta a janela do painel de monitoramento
  static void montarInterface() {
    JFrame janela = new JFrame("Monitor da Caldeira — Alarms");
    janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JPanel raiz = new JPanel(new BorderLayout(8, 8));
    raiz.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    // Centro: gráfico da média (acima) e log de alarmes (abaixo)
    grafico = new GraficoMedia();
    grafico.setPreferredSize(new Dimension(680, 240));
    grafico.setBorder(BorderFactory.createTitledBorder("Média das temperaturas (janela de 120s)"));
    logAlarmes = new JTextArea(9, 80);
    logAlarmes.setEditable(false);
    JScrollPane logScroll = new JScrollPane(logAlarmes);
    logScroll.setBorder(BorderFactory.createTitledBorder("Alarmes (caldeira/alarme)"));
    JSplitPane centro = new JSplitPane(JSplitPane.VERTICAL_SPLIT, grafico, logScroll);
    centro.setResizeWeight(0.55);
    raiz.add(centro, BorderLayout.CENTER);

    // Sul: painel do simulador de sensores
    raiz.add(montarPainelSimulador(), BorderLayout.SOUTH);

    janela.setContentPane(raiz);
    janela.pack();
    janela.setLocationRelativeTo(null);
    janela.setVisible(true);

    // Busca a configuração atual do simulador (fora da EDT, é uma chamada remota)
    new Thread(Alarms::carregarConfiguracao).start();
  }

  // Painel "Simulador de Sensores": base, faixa de variação, intervalo e
  // quantidade de sensores, além do botão iniciar/parar.
  // Sem o serviço Python o painel fica coberto por um véu cinza com o aviso
  // "Random Generator Service Unavailable" e nenhum campo pode ser tocado.
  static JPanel montarPainelSimulador() {
    painelSimulador = new JPanel(new GridLayout(0, 1, 6, 6)) {
      @Override
      public void paint(Graphics g) {
        // pinta o painel normal (campos etc.) e depois o véu por cima
        super.paint(g);
        if (!simuladorDisponivel) {
          g.setColor(new Color(110, 110, 110, 180));
          g.fillRect(0, 0, getWidth(), getHeight());
          g.setColor(Color.WHITE);
          g.setFont(g.getFont().deriveFont(Font.BOLD, 16f));
          String aviso = "Random Generator Service Unavailable";
          int largura = g.getFontMetrics().stringWidth(aviso);
          g.drawString(aviso, (getWidth() - largura) / 2, getHeight() / 2);
        }
      }
    };
    painelSimulador.setBorder(BorderFactory.createTitledBorder("Simulador de Sensores"));

    JPanel campos = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
    campoBase = new JTextField(6);
    campoMinimo = new JTextField(4);
    campoMaximo = new JTextField(4);
    campoIntervalo = new JTextField(4);
    campoSensores = new JTextField(4);
    campos.add(new JLabel("Base (°C):"));
    campos.add(campoBase);
    campos.add(new JLabel("Variação mínima:"));
    campos.add(campoMinimo);
    campos.add(new JLabel("Variação máxima:"));
    campos.add(campoMaximo);
    campos.add(new JLabel("Intervalo (ms):"));
    campos.add(campoIntervalo);
    campos.add(new JLabel("Sensores:"));
    campos.add(campoSensores);

    JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
    botaoIniciar = new JButton("Iniciar");
    botaoIniciar.addActionListener(e -> alternarSimulador());
    // Atualizar envia a configuração sem mexer no estado iniciar/parar;
    // só fica habilitado quando os campos diferem da configuração atual
    botaoAtualizar = new JButton("Atualizar");
    botaoAtualizar.addActionListener(e -> atualizarConfiguracao());
    botaoAtualizar.setEnabled(false);
    statusSimulador = new JLabel("Buscando o simulador...");
    controles.add(botaoIniciar);
    controles.add(botaoAtualizar);
    controles.add(statusSimulador);
    escutarCampos();

    painelSimulador.add(campos);
    painelSimulador.add(controles);

    // Começa travado até buscar a configuração do serviço Python
    definirHabilitado(false);
    return painelSimulador;
  }

  // Na inicialização busca a configuração atual do simulador.
  // Se o serviço não responde, o painel permanece bloqueado com o aviso.
  static void carregarConfiguracao() {
    String resposta = null;
    try {
      resposta = chamarHttp("GET", URL_SIMULADOR + "/config", null);
    } catch (Exception e) {
      System.out.println("Simulador indisponível: " + e.getMessage());
    }
    Map<String, Object> config = resposta != null ? Json.fromJson(resposta) : null;
    final boolean disponivel = config != null;
    final Map<String, Object> dados = config;
    SwingUtilities.invokeLater(() -> {
      simuladorDisponivel = disponivel;
      if (disponivel) {
        campoBase.setText(formatar(Json.numero(dados, "base")));
        campoMinimo.setText(formatar(Json.numero(dados, "minimo")));
        campoMaximo.setText(formatar(Json.numero(dados, "maximo")));
        campoIntervalo.setText(formatar(Json.numero(dados, "intervaloMillis")));
    campoSensores.setText(formatar(Json.numero(dados, "sensores")));
        configSalva = dados; // os campos carregados ainda não foram alterados
        botaoAtualizar.setEnabled(false);
        statusSimulador.setText("Simulador conectado — parado");
      } else {
        statusSimulador.setText("Random Generator Service Unavailable");
      }
      definirHabilitado(disponivel);
      painelSimulador.repaint();
    });
  }

  // Envia a configuração dos campos e alterna iniciar/parar no simulador
  static void alternarSimulador() {
    // Valida os campos antes de chamar o serviço
    final Map<String, Object> config;
    try {
      config = montarConfig();
    } catch (NumberFormatException e) {
      statusSimulador.setText("Valores inválidos nos campos");
      return;
    }
    final boolean iniciar = !simulando;
    botaoIniciar.setEnabled(false);
    statusSimulador.setText("Enviando...");
    // Chamada remota fora da EDT para não travar a interface
    new Thread(() -> {
      String mensagemErro = null;
      String respostaConfig = null;
      try {
        respostaConfig = chamarHttp("POST", URL_SIMULADOR + "/config", Json.toJson(config));
        chamarHttp("POST", URL_SIMULADOR + "/control", "{\"acao\":\"" + (iniciar ? "iniciar" : "parar") + "\"}");
      } catch (Exception e) {
        mensagemErro = e.getMessage();
      }
      final String erro = mensagemErro;
      final Map<String, Object> salvo = respostaConfig != null ? Json.fromJson(respostaConfig) : null;
      SwingUtilities.invokeLater(() -> {
        botaoIniciar.setEnabled(true);
        if (erro != null) {
          statusSimulador.setText("Falha no simulador: " + erro);
          botaoAtualizar.setEnabled(configuracaoAlterada());
          return;
        }
        simulando = iniciar;
        botaoIniciar.setText(simulando ? "Parar" : "Iniciar");
        statusSimulador.setText(simulando ? "Simulando" : "Simulador conectado — parado");
        if (salvo != null) {
          // a configuração dos campos acabou de ser aceita pelo simulador
          configSalva = salvo;
          botaoAtualizar.setEnabled(false);
        }
      });
    }).start();
  }

  // Lê os campos e monta a configuração do simulador como Map
  static Map<String, Object> montarConfig() throws NumberFormatException {
    Map<String, Object> campos = new LinkedHashMap<>();
    campos.put("base", Double.parseDouble(campoBase.getText().trim()));
    campos.put("minimo", Double.parseDouble(campoMinimo.getText().trim()));
    campos.put("maximo", Double.parseDouble(campoMaximo.getText().trim()));
    campos.put("intervaloMillis", Integer.parseInt(campoIntervalo.getText().trim()));
    campos.put("sensores", Integer.parseInt(campoSensores.getText().trim()));
    return campos;
  }

  // Confere se algum campo difere da última configuração conhecida do simulador
  static boolean configuracaoAlterada() {
    if (configSalva == null) {
      return false;
    }
    try {
      Map<String, Object> atual = montarConfig();
      for (Map.Entry<String, Object> campo : atual.entrySet()) {
        double valorAtual = ((Number) campo.getValue()).doubleValue();
        Double valorSalvo = Json.numero(configSalva, campo.getKey());
        if (valorSalvo == null || valorAtual != valorSalvo) {
          return true;
        }
      }
      return false;
    } catch (NumberFormatException e) {
      return false; // campos inválidos: o clique em enviar mostra o erro
    }
  }

  // Envia a configuração dos campos sem alterar o estado iniciar/parar
  static void atualizarConfiguracao() {
    final Map<String, Object> config;
    try {
      config = montarConfig();
    } catch (NumberFormatException e) {
      statusSimulador.setText("Valores inválidos nos campos");
      return;
    }
    botaoAtualizar.setEnabled(false);
    statusSimulador.setText("Enviando...");
    // Chamada remota fora da EDT para não travar a interface
    new Thread(() -> {
      String mensagemErro = null;
      String resposta = null;
      try {
        resposta = chamarHttp("POST", URL_SIMULADOR + "/config", Json.toJson(config));
      } catch (Exception e) {
        mensagemErro = e.getMessage();
      }
      final String erro = mensagemErro;
      final Map<String, Object> salvo = resposta != null ? Json.fromJson(resposta) : null;
      SwingUtilities.invokeLater(() -> {
        if (erro != null) {
          statusSimulador.setText("Falha no simulador: " + erro);
          botaoAtualizar.setEnabled(true);
          return;
        }
        configSalva = salvo != null ? salvo : config;
        botaoAtualizar.setEnabled(false);
        statusSimulador.setText("Configuração atualizada");
      });
    }).start();
  }

  // Habilita o botão Atualizar quando os campos mudarem em relação à configuração
  static void escutarCampos() {
    DocumentListener ouvinte = new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent evento) {
        botaoAtualizar.setEnabled(configuracaoAlterada());
      }

      @Override
      public void removeUpdate(DocumentEvent evento) {
        botaoAtualizar.setEnabled(configuracaoAlterada());
      }

      @Override
      public void changedUpdate(DocumentEvent evento) {
        botaoAtualizar.setEnabled(configuracaoAlterada());
      }
    };
    campoBase.getDocument().addDocumentListener(ouvinte);
    campoMinimo.getDocument().addDocumentListener(ouvinte);
    campoMaximo.getDocument().addDocumentListener(ouvinte);
    campoIntervalo.getDocument().addDocumentListener(ouvinte);
    campoSensores.getDocument().addDocumentListener(ouvinte);
  }

  // Habilita ou trava todos os campos do painel do simulador
  static void definirHabilitado(boolean habilitado) {
    campoBase.setEnabled(habilitado);
    campoMinimo.setEnabled(habilitado);
    campoMaximo.setEnabled(habilitado);
    campoIntervalo.setEnabled(habilitado);
    campoSensores.setEnabled(habilitado);
    botaoIniciar.setEnabled(habilitado);
    botaoAtualizar.setEnabled(habilitado && configuracaoAlterada());
  }

  // Chamada remota ao simulador (HTTP + JSON)
  static String chamarHttp(String metodo, String urlStr, String corpo) throws IOException {
    try {
      HttpURLConnection conn = (HttpURLConnection) new URI(urlStr).toURL().openConnection();
      conn.setRequestMethod(metodo);
      conn.setConnectTimeout(3000);
      conn.setReadTimeout(3000);
      if (corpo != null) {
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream saida = conn.getOutputStream()) {
          saida.write(corpo.getBytes(StandardCharsets.UTF_8));
        }
      }
      int codigo = conn.getResponseCode();
      InputStream stream = codigo / 100 == 2 ? conn.getInputStream() : conn.getErrorStream();
      String resposta = stream == null ? "" : lerTudo(stream);
      if (codigo / 100 != 2) {
        throw new IOException("HTTP " + codigo + ": " + resposta);
      }
      return resposta;
    } catch (URISyntaxException e) {
      throw new IOException("URL inválida: " + urlStr);
    }
  }

  // Lê todo o stream e fecha
  static String lerTudo(InputStream stream) throws IOException {
    try (BufferedReader leitor = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return leitor.lines().collect(Collectors.joining());
    }
  }

  // Formata um número para preencher os campos (sem ".0" em inteiros)
  static String formatar(Double valor) {
    if (valor == null) {
      return "";
    }
    return valor == Math.floor(valor) ? String.valueOf(valor.longValue()) : String.valueOf(valor);
  }

  // Gráfico de linha simples da média das temperaturas ao longo do tempo
  static class GraficoMedia extends JPanel {
    static final int MAX_PONTOS = 300;

    // Pontos {timestamp em ms, média}
    final Deque<double[]> pontos = new ArrayDeque<>();

    void adicionar(long timestamp, double media) {
      synchronized (pontos) {
        pontos.addLast(new double[]{timestamp, media});
        while (pontos.size() > MAX_PONTOS) {
          pontos.pollFirst();
        }
      }
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      int largura = getWidth();
      int altura = getHeight();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, largura, altura);

      // Área do gráfico: margens para os valores (esquerda) e horários (embaixo)
      int x0 = 52;           // espaço para os valores do eixo Y
      int x1 = largura - 10;
      int y0 = 20;           // espaço para o título da borda
      int y1 = altura - 26;  // espaço para os horários do eixo X

      synchronized (pontos) {
        if (pontos.isEmpty()) {
          g.setColor(Color.GRAY);
          g.drawString("Aguardando dados dos sensores...", x0 + 10, (y0 + y1) / 2);
          return;
        }

        // Escala vertical com folga
        double minimo = Double.MAX_VALUE;
        double maximo = -Double.MAX_VALUE;
        for (double[] ponto : pontos) {
          minimo = Math.min(minimo, ponto[1]);
          maximo = Math.max(maximo, ponto[1]);
        }
        double folga = Math.max(1.0, (maximo - minimo) * 0.15);
        minimo -= folga;
        maximo += folga;

        // Grade horizontal com os valores do eixo Y à esquerda
        for (int i = 0; i <= 4; i++) {
          double valor = maximo - (maximo - minimo) * i / 4.0;
          int y = paraY(valor, minimo, maximo, y0, y1);
          g.setColor(Color.LIGHT_GRAY);
          g.drawLine(x0, y, x1, y);
          g.setColor(Color.DARK_GRAY);
          String rotulo = String.format("%.1f", valor);
          int larguraRotulo = g.getFontMetrics().stringWidth(rotulo);
          g.drawString(rotulo, x0 - larguraRotulo - 4, y + 4);
        }

        // Linha do limite de temperatura alta (200 °C)
        if (200 >= minimo && 200 <= maximo) {
          int y = paraY(200, minimo, maximo, y0, y1);
          g.setColor(Color.RED);
          g.drawLine(x0, y, x1, y);
          String rotulo = "200°C";
          g.drawString(rotulo, x1 - g.getFontMetrics().stringWidth(rotulo) - 4, y - 4);
        }

        // Linha da média: eixo X é a posição do ponto na sequência
        g.setColor(Color.BLUE);
        int n = pontos.size();
        double[][] serie = pontos.toArray(new double[0][]);
        int xAnterior = -1;
        int yAnterior = -1;
        for (int i = 0; i < n; i++) {
          int x = n == 1 ? x1 : x0 + (x1 - x0) * i / (n - 1);
          int y = paraY(serie[i][1], minimo, maximo, y0, y1);
          if (xAnterior >= 0) {
            g.drawLine(xAnterior, yAnterior, x, y);
          }
          xAnterior = x;
          yAnterior = y;
        }

        // Horários no eixo X (primeiro, meio e último ponto)
        g.setColor(Color.DARK_GRAY);
        String primeiro = hora((long) serie[0][0]);
        String meio = hora((long) serie[n / 2][0]);
        String ultimo = hora((long) serie[n - 1][0]);
        int yRotulo = altura - 8;
        g.drawString(primeiro, x0, yRotulo);
        int larguraMeio = g.getFontMetrics().stringWidth(meio);
        g.drawString(meio, (x0 + x1) / 2 - larguraMeio / 2, yRotulo);
        g.drawString(ultimo, x1 - g.getFontMetrics().stringWidth(ultimo), yRotulo);

        // Valor atual no canto direito, longe do título da borda
        double atual = serie[n - 1][1];
        String texto = String.format("média atual: %.1f°C (%d pontos)", atual, n);
        g.setColor(Color.BLACK);
        g.drawString(texto, x1 - g.getFontMetrics().stringWidth(texto) - 2, 16);
      }
    }

    // Formata um timestamp (em ms) como HH:mm:ss
    private static String hora(long timestamp) {
      return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
          .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    // Converte um valor de temperatura para a coordenada Y dentro da área do gráfico
    private static int paraY(double valor, double minimo, double maximo, int y0, int y1) {
      double fracao = (valor - minimo) / (maximo - minimo);
      return y1 - (int) (fracao * (y1 - y0));
    }
  }

}