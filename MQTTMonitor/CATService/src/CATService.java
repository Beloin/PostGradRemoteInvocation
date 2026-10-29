import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

// Serviço CAT (Compute Average Temperature):
// - inscrito nos tópicos de temperatura dos sensores
// - calcula a média das temperaturas dos últimos 120 segundos
// - publica eventos: "aumento-repentina" (duas últimas médias diferem 5 graus)
//   e "temperatura-alta" (média acima de 200 graus)
public class CATService {

  // Tópicos
  static final String TOPICO_TEMPERATURA = "caldeira/sensores/+/temperatura";
  static final String TOPICO_EVENTO = "caldeira/cat/evento";

  // Intervalo da média móvel em milissegundos: 120 segundos
  static final long JANELA_MEDIA_MILLIS = 120_000;

  public static void main(String[] args) {
    String broker = "tcp://localhost:1883";
    try {
      MqttClient client = new MqttClient(broker, "cat-service", new MemoryPersistence());
      MqttConnectOptions opcoes = new MqttConnectOptions();
      opcoes.setAutomaticReconnect(true);
      opcoes.setCleanSession(true);
      client.connect(opcoes);

      // Inscrição: cada mensagem de temperatura entra na média móvel
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
    // TODO: guardar (timestamp, valor) da leitura; descartar leituras mais antigas que 120s;
    // TODO: calcular a média e publicar eventos em TOPICO_EVENTO conforme o enunciado
  }

}