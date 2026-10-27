import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Scanner;

public class CalculadoraClientSocket {

  public static void main(String[] args) {
    Scanner teclado = new Scanner(System.in);
    try {
      // Conexão com o Servidor
      Socket clientSocket = new Socket("localhost", 9090);
      DataOutputStream socketSaidaServer = new DataOutputStream(clientSocket.getOutputStream());
      BufferedReader messageFromServer = new BufferedReader(
          new InputStreamReader(clientSocket.getInputStream()));

      while (true) {
        // Limpa a tela (código ANSI de escape)
        System.out.print("\033[H\033[2J");
        System.out.flush();

        System.out.println("----- Calculadora -----");
        System.out.println("1 - Somar");
        System.out.println("2 - Subtrair");
        System.out.println("3 - Dividir");
        System.out.println("4 - Multiplicar");
        System.out.println("5 - Expressão com parênteses");
        System.out.println("0 - Sair");
        System.out.print("Escolha a operação: ");
        int operacao = Integer.parseInt(teclado.nextLine());

        if (operacao == 0) {
          break;
        }
        if (operacao < 1 || operacao > 5) {
          continue;
        }

        double resultado;
        if (operacao == 5) {
          resultado = calcularExpressao(teclado, socketSaidaServer, messageFromServer);
        } else {
          System.out.print("Informe o primeiro número: ");
          double oper1 = Double.parseDouble(teclado.nextLine());
          System.out.print("Informe o segundo número: ");
          double oper2 = Double.parseDouble(teclado.nextLine());

          // Enviando os dados
          socketSaidaServer.writeBytes(operacao + "\n" + oper1 + "\n" + oper2 + "\n");
          socketSaidaServer.flush();

          // Recebendo a resposta
          resultado = Double.parseDouble(messageFromServer.readLine());
        }

        System.out.println("resultado=" + resultado);

        System.out.print("Pressione ENTER para continuar...");
        teclado.nextLine();
      }

      clientSocket.close();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      teclado.close();
    }
  }

  // Avalia uma expressão matemática com parênteses em dois modos
  private static double calcularExpressao(Scanner teclado, DataOutputStream socketSaidaServer,
      BufferedReader messageFromServer) throws IOException {
    System.out.println("Modo de execução:");
    System.out.println("1 - Cliente decompõe em operações básicas no servidor");
    System.out.println("2 - Expressão calculada inteiramente no servidor");
    System.out.print("Escolha o modo: ");
    int modo = Integer.parseInt(teclado.nextLine());
    System.out.print("Expressão (ex: (10 + 15) * 4): ");
    String expressao = teclado.nextLine();

    if (modo == 2) {
      // Envia "expr" + a expressão: o servidor calcula tudo
      socketSaidaServer.writeBytes("expr\n" + expressao + "\n");
      socketSaidaServer.flush();
      return Double.parseDouble(messageFromServer.readLine());
    }
    if (modo == 1) {
      // O cliente decompõe: cada operador vira uma operação básica no servidor
      return Avaliador.avaliar(expressao, (op, a, b) -> {
        try {
          socketSaidaServer.writeBytes(op + "\n" + a + "\n" + b + "\n");
          socketSaidaServer.flush();
          return Double.parseDouble(messageFromServer.readLine());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
    throw new RuntimeException("modo invalido");
  }

}
