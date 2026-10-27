import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Tokenizer é uma máquina de estados sobre os caracteres da expressão;
 * shunting-yard monta a fila pós-fixa usando uma pilha de operadores;
 * avaliar percorre a fila pós-fixa com uma pilha de operandos.
 * Cada operador binário é delegado para aplicar.op(op, a, b).
 */
public final class Avaliador {

  /** Operador binário: op 1 soma, 2 subtracao, 3 divisao, 4 multiplicacao */
  public interface OperacaoBinaria {
    double aplicar(int op, double a, double b);
  }

  private Avaliador() {
  }

  // Tokeniza a expressão: números com ponto, + - * /, parênteses
  public static List<String> tokenizar(String expressao) {
    List<String> tokens = new ArrayList<>();
    int estado = 0; // 0 = fora de um número, 1 = dentro de um número
    StringBuilder numero = new StringBuilder();
    for (char c : expressao.toCharArray()) {
      if (c >= '0' && c <= '9' || c == '.') {
        if (estado == 0) {
          numero.setLength(0);
          estado = 1;
        }
        numero.append(c);
      } else {
        if (estado == 1) {
          tokens.add(numero.toString());
          estado = 0;
        }
        if (c == '(' || c == ')' || c == '+' || c == '-' || c == '*' || c == '/') {
          tokens.add(String.valueOf(c));
        } else if (c != ' ') {
          throw new IllegalArgumentException("caractere invalido: " + c);
        }
      }
    }
    if (estado == 1) {
      tokens.add(numero.toString());
    }
    return tokens;
  }

  private static int precedencia(String op) {
    switch (op) {
      case "+":
      case "-":
        return 1;
      case "*":
      case "/":
        return 2;
    }
    return 0;
  }

  private static int codigo(String op) {
    switch (op) {
      case "+": return 1;
      case "-": return 2;
      case "/": return 3;
      case "*": return 4;
    }
    throw new IllegalArgumentException("operador invalido: " + op);
  }

  // Shunting-yard: converte a notação infixa para pós-fixa (RPN)
  public static Deque<String> posfixa(String expressao) {
    List<String> tokens = tokenizar(expressao);
    Deque<String> fila = new ArrayDeque<>();
    Deque<String> pilha = new ArrayDeque<>(); // pilha de operadores
    for (String token : tokens) {
      if (token.equals("(")) {
        pilha.push(token);
      } else if (token.equals(")")) {
        while (!pilha.isEmpty() && !pilha.peek().equals("(")) {
          fila.add(pilha.pop());
        }
        if (pilha.isEmpty()) {
          throw new IllegalArgumentException("parêntese desbalanceado");
        }
        pilha.pop();
      } else if (precedencia(token) > 0) {
        while (!pilha.isEmpty() && precedencia(pilha.peek()) >= precedencia(token)) {
          fila.add(pilha.pop());
        }
        pilha.push(token);
      } else {
        fila.add(token); // número
      }
    }
    while (!pilha.isEmpty()) {
      String op = pilha.pop();
      if (op.equals("(")) {
        throw new IllegalArgumentException("parêntese desbalanceado");
      }
      fila.add(op);
    }
    return fila;
  }

  // Avalia a expressão; cada operador chama aplicar
  public static double avaliar(String expressao, OperacaoBinaria aplicar) {
    Deque<Double> pilha = new ArrayDeque<>(); // pilha de operandos
    for (String token : posfixa(expressao)) {
      if (precedencia(token) > 0) {
        if (pilha.size() < 2) {
          throw new IllegalArgumentException("expressão invalida");
        }
        double b = pilha.pop();
        double a = pilha.pop();
        pilha.push(aplicar.aplicar(codigo(token), a, b));
      } else {
        pilha.push(Double.parseDouble(token));
      }
    }
    if (pilha.size() != 1) {
      throw new IllegalArgumentException("expressão invalida");
    }
    return pilha.pop();
  }
}
