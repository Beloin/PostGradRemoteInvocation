import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.LinkedHashMap;
import java.util.Map;

// Encapsula a biblioteca Gson: os serviços só enxergam Map<String, Object>
final class Json {

  private static final Gson GSON = new Gson();

  private Json() {
  }

  // Serializa um Map em JSON; LinkedHashMap mantém a ordem das chaves
  static String toJson(Map<String, Object> campos) {
    return GSON.toJson(campos);
  }

  // Faz o parse do JSON para Map<String, Object>; null se não for um objeto válido.
  // Números viram Double, boolean viram Boolean, o resto vira String.
  static Map<String, Object> fromJson(String texto) {
    try {
      JsonElement raiz = JsonParser.parseString(texto);
      if (!raiz.isJsonObject()) {
        return null;
      }
      Map<String, Object> campos = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> campo : raiz.getAsJsonObject().entrySet()) {
        JsonElement valor = campo.getValue();
        if (valor.isJsonPrimitive()) {
          JsonPrimitive primitivo = valor.getAsJsonPrimitive();
          if (primitivo.isNumber()) {
            campos.put(campo.getKey(), primitivo.getAsDouble());
          } else if (primitivo.isBoolean()) {
            campos.put(campo.getKey(), primitivo.getAsBoolean());
          } else {
            campos.put(campo.getKey(), primitivo.getAsString());
          }
        } else {
          campos.put(campo.getKey(), valor.toString()); // objeto/array aninhado
        }
      }
      return campos;
    } catch (JsonSyntaxException e) {
      return null;
    }
  }

  // Lê um campo numérico do Map; null se não existir ou não for número
  static Double numero(Map<String, Object> campos, String chave) {
    Object valor = campos.get(chave);
    return valor instanceof Double ? (Double) valor : null;
  }

}