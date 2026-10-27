import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class CalculadoraCliente {

	public static void main(String[] args) {
		Registry reg = null;
		ICalculadora calc = null;
		try {
			reg = LocateRegistry.getRegistry(1099);
			calc = (ICalculadora) reg.lookup("calculadora");
		} catch (RemoteException | NotBoundException e) {
			System.out.println(e);
			System.exit(0);
		}
		// referência final para usar dentro da lambda
		final ICalculadora servico = calc;

		Scanner teclado = new Scanner(System.in);
		while (true) {
			// Limpa a tela (código ANSI de escape)
			System.out.print("\033[H\033[2J");
			System.out.flush();

			System.out.println("----- Calculadora RMI -----");
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

			try {
				double resultado;
				if (operacao == 5) {
					System.out.println("Modo de execução:");
					System.out.println("1 - Cliente decompõe em operações básicas remotas");
					System.out.println("2 - Expressão calculada inteiramente no servidor");
					System.out.print("Escolha o modo: ");
					int modo = Integer.parseInt(teclado.nextLine());
					System.out.print("Expressão (ex: (10 + 15) * 4): ");
					String expressao = teclado.nextLine();

					if (modo == 2) {
						// O servidor avalia a expressão inteira
						resultado = servico.avaliar(expressao);
					} else if (modo == 1) {
						// O cliente decompõe: cada operador vira uma chamada remota
						resultado = Avaliador.avaliar(expressao, (op, a, b) -> {
							try {
								return chamar(servico, op, (int) a, (int) b);
							} catch (RemoteException e) {
								throw new RuntimeException(e);
							}
						});
					} else {
						continue;
					}
				} else {
					System.out.print("Informe o primeiro número: ");
					int a = Integer.parseInt(teclado.nextLine());
					System.out.print("Informe o segundo número: ");
					int b = Integer.parseInt(teclado.nextLine());
					resultado = chamar(servico, operacao, a, b);
				}
				System.out.println("resultado=" + resultado);
			} catch (RemoteException e) {
				System.out.println(e);
				break;
			}

			System.out.print("Pressione ENTER para continuar...");
			teclado.nextLine();
		}
		teclado.close();
	}

	// Chamada remota de uma operação básica
	private static double chamar(ICalculadora calc, int op, int a, int b) throws RemoteException {
		switch (op) {
			case 1: return calc.soma(a, b);
			case 2: return calc.subtracao(a, b);
			case 3: return calc.divisao(a, b);
			case 4: return calc.multiplicacao(a, b);
		}
		return 0;
	}

}
