import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

// Serviço CAT (Compute Average Temperature):
// - inscrito nos tópicos de temperatura dos sensores
// - guarda as leituras dos últimos 120 segundos e calcula a média
// - publica a média em caldeira/cat/media (o painel Alarms desenha o gráfico)
// - publica eventos em caldeira/cat/evento:
//   "aumento-repentina" quando as duas últimas médias diferem 5 graus ou mais,
//   "temperatura-alta" quando a média fica acima de 200 graus
public class CATService {

  // Tópicos
  static final String TOPICO_TEMPERATURA = "caldeira/sensores/+/temperatura";
  static final String TOPICO_EVENTO = "caldeira/cat/evento";
  static final String TOPICO_MEDIA = "caldeira/cat/media";

  // Regras do enunciado
  static final long JANELA_MEDIA_MILLIS = 120_000;
  static final double LIMITE_SALTO_MEDIA = 5.0;
  static final double LIMITE_TEMPERATURA_ALTA = 200.0;

  static MqttClient client;

  // Leituras dentro da janela de 120s: pares {timestamp em ms, temperatura}
  static final Deque<double[]> leituras = new ArrayDeque<>();
  static Double mediaAnterior = null;

  public static void main(String[] args) {
    try {
      client = new MqttClient("tcp://localhost:1883", "cat-service", new MemoryPersistence());
      MqttConnectOptions opcoes = new MqttConnectOptions();
      opcoes.setAutomaticReconnect(true);
      opcoes.setCleanSession(true);
      client.connect(opcoes);

      // Inscrição: cada temperatura recebida atualiza a média e dispara os eventos
      client.subscribe(TOPICO_TEMPERATURA, (topico, mensagem) -> receberTemperatura(topico, mensagem));

      System.out.println("Servidor no ar (CAT inscrito em " + TOPICO_TEMPERATURA + ")");
    } catch (MqttException e) {
      e.printStackTrace();
    }
  }

  // Trata uma temperatura recebida de um sensor
  static void receberTemperatura(String topico, MqttMessage mensagem) {
    String payload = new String(mensagem.getPayload());
    System.out.println("Recebido " + topico + " -> " + payload);

    long agora = System.currentTimeMillis();

    // Lê o payload como Map (aceita qualquer ordem de campos); null ignora a leitura
    Map<String, Object> dados = Json.fromJson(payload);
    if (dados == null) {
      return; // payload fora do padrão: ignora a leitura
    }
    Double valor = Json.numero(dados, "temperatura");
    if (valor == null) {
      return; // payload sem temperatura: ignora a leitura
    }
    Double timestampPayload = Json.numero(dados, "timestamp");
    long timestamp = timestampPayload != null ? timestampPayload.longValue() : agora;

    synchronized (leituras) {
      // Guarda a leitura e descarta as mais antigas que 120 segundos
      leituras.addLast(new double[]{timestamp, valor});
      while (!leituras.isEmpty() && leituras.peekFirst()[0] < agora - JANELA_MEDIA_MILLIS) {
        leituras.pollFirst();
      }
      if (leituras.isEmpty()) {
        return;
      }

      // Média das leituras restantes na janela
      double soma = 0;
      for (double[] leitura : leituras) {
        soma += leitura[1];
      }
      double media = soma / leituras.size();
      System.out.println("Media calculada: " + media + " (" + leituras.size() + " leituras)");

      // Publica a média para o gráfico do painel de monitoramento
      Map<String, Object> mensagemMedia = new LinkedHashMap<>();
      mensagemMedia.put("media", media);
      mensagemMedia.put("timestamp", agora);
      publicar(TOPICO_MEDIA, Json.toJson(mensagemMedia));

      // Evento: aumento repentina de temperatura (duas últimas médias diferem 5 graus)
      if (mediaAnterior != null && Math.abs(media - mediaAnterior) >= LIMITE_SALTO_MEDIA) {
        Map<String, Object> eventoSalto = new LinkedHashMap<>();
        eventoSalto.put("tipo", "aumento-repentina");
        eventoSalto.put("mediaAnterior", mediaAnterior);
        eventoSalto.put("mediaAtual", media);
        publicar(TOPICO_EVENTO, Json.toJson(eventoSalto));
      }
      // Evento: temperatura alta (média acima de 200 graus)
      if (media > LIMITE_TEMPERATURA_ALTA) {
        Map<String, Object> eventoAlta = new LinkedHashMap<>();
        eventoAlta.put("tipo", "temperatura-alta");
        eventoAlta.put("media", media);
        publicar(TOPICO_EVENTO, Json.toJson(eventoAlta));
      }
      mediaAnterior = media;
    }
  }

  // Publica um payload em um tópico
  static void publicar(String topico, String payload) {
    try {
      client.publish(topico, new MqttMessage(payload.getBytes()));
    } catch (MqttException e) {
      e.printStackTrace();
    }
  }

}