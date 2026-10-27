import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class CalculadoraServerSocket {

  public static void main(String[] args) {
    // TODO Auto-generated method stub
    ServerSocket welcomeSocket;
    Calculadora calc = new Calculadora();
    try {
      welcomeSocket = new ServerSocket(9090);
      int i = 0; // número de clientes

      System.out.println("Servidor no ar");
      while (true) {

        Socket connectionSocket = welcomeSocket.accept();
        int cliente = ++i;
        System.out.println("Nova conexão (" + cliente + ")");

        // Uma thread por cliente: a mesma conexão atende várias requisições
        new Thread(() -> atende(connectionSocket, calc, cliente)).start();

      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  // Atende um cliente até ele se desconectar
  private static void atende(Socket connectionSocket, Calculadora calc, int numeroCliente) {
    try {
      // Interpretando dados do servidor
      BufferedReader socketEntrada = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
      DataOutputStream socketOutput = new DataOutputStream(connectionSocket.getOutputStream());

      // Uma requisição por iteração: <operacao>\n<oper1>\n<oper2> ou "expr"\n<expressao>
      while (true) {
        String result;
        try {
          String linhaOperacao = socketEntrada.readLine();
          if (linhaOperacao == null) {
            break; // cliente desconectou
          }
          // Expressão com parênteses: calculada inteiramente no servidor
          if (linhaOperacao.equals("expr")) {
            String expressao = socketEntrada.readLine();
            result = "" + Avaliador.avaliar(expressao, (op, a, b) -> {
              switch (op) {
                case 1: return calc.soma(a, b);
                case 2: return calc.subtracao(a, b);
                case 3: return calc.divisao(a, b);
                case 4: return calc.multiplicacao(a, b);
              }
              return Double.NaN;
            });
          } else {
            int operacao = Integer.parseInt(linhaOperacao);
            double oper1 = Double.parseDouble(socketEntrada.readLine());
            double oper2 = Double.parseDouble(socketEntrada.readLine());

            // Chamando a calculadora
            double resultado = switch (operacao) {
              case 1 -> calc.soma(oper1, oper2);
              case 2 -> calc.subtracao(oper1, oper2);
              case 3 -> calc.divisao(oper1, oper2);
              case 4 -> calc.multiplicacao(oper1, oper2);
              default -> Double.NaN;
            };
            result = "" + resultado;
          }
        } catch (IllegalArgumentException e) {
          result = "Erro: dados invalidos";
        }

        // Enviando dados para o cliente
        socketOutput.writeBytes(result + '\n');
        System.out.println(result);
        socketOutput.flush();
      }
    } catch (IOException e) {
      // conexão encerrada abruptamente
    } finally {
      System.out.println("Cliente " + numeroCliente + " desconectou");
      try {
        connectionSocket.close();
      } catch (IOException e) {
        // nada a fazer
      }
    }
  }

}
