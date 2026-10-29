import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

// Serviço Alarms:
// - monitora os eventos publicados pelo CAT ("aumento-repentina", "temperatura-alta")
// - publica/apresenta um alarme na interface do sistema de monitoramento
// Versão inicial: imprime o alarme no console (pode evoluir para interface gráfica)
public class Alarms {

  // Tópico onde chegam os eventos do CAT
  static final String TOPICO_EVENTO = "caldeira/cat/evento";

  // Tópico onde o alarme é publicado
  static final String TOPICO_ALARMES = "caldeira/alarme";

  public static void main(String[] args) {
    String broker = "tcp://localhost:1883";
    try {
      MqttClient client = new MqttClient(broker, "servico-alarms", new MemoryPersistence());
      MqttConnectOptions opcoes = new MqttConnectOptions();
      opcoes.setAutomaticReconnect(true);
      opcoes.setCleanSession(true);
      client.connect(opcoes);

      // Inscrição: cada evento do CAT vira um alarme
      client.subscribe(TOPICO_EVENTO, (topico, mensagem) -> dispararAlarme(mensagem));

      System.out.println("Servidor no ar (Alarms inscrito em " + TOPICO_EVENTO + ")");
    } catch (MqttException e) {
      e.printStackTrace();
    }
  }

  // Apresenta o alarme ao operador (console, por enquanto)
  static void dispararAlarme(MqttMessage mensagem) {
    // TODO: publicar também no tópico TOPICO_ALARMES e desenhar na interface gráfica
    System.out.println("[ALARM] " + new String(mensagem.getPayload()));
  }

}