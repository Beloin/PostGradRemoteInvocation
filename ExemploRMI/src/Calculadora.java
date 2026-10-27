import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Calculadora  implements ICalculadora {

	private static final long serialVersionUID = 1L;

	private static int chamadas = 0;

	public int soma(int a, int b) throws RemoteException {
		System.out.println("Método soma chamado " + chamadas++);
		return a + b;
	}

	public int subtracao(int a, int b) throws RemoteException {
		System.out.println("Método subtracao chamado " + chamadas++);
		return a - b;
	}

	public int divisao(int a, int b) throws RemoteException {
		System.out.println("Método divisao chamado " + chamadas++);
		return a / b;
	}

	public int multiplicacao(int a, int b) throws RemoteException {
		System.out.println("Método multiplicacao chamado " + chamadas++);
		return a * b;
	}

	public double avaliar(String expressao) throws RemoteException {
		System.out.println("Método avaliar chamado " + chamadas++);
		try {
			// Expressão com parênteses calculada inteiramente no servidor
			return Avaliador.avaliar(expressao, (op, a, b) -> {
				try {
					switch (op) {
						case 1: return soma((int) a, (int) b);
						case 2: return subtracao((int) a, (int) b);
						case 3: return divisao((int) a, (int) b);
						case 4: return multiplicacao((int) a, (int) b);
					}
					return 0;
				} catch (RemoteException e) {
					// não é esperado em chamada local, mas a lambda não declara RemoteException
					throw new RuntimeException(e);
				}
			});
		} catch (RuntimeException e) {
			if (e.getCause() instanceof RemoteException) {
				throw (RemoteException) e.getCause();
			}
			throw new RemoteException("Erro ao avaliar expressão", e);
		}
	}

	public static void main(String[] args) throws AccessException, RemoteException, AlreadyBoundException  {
		Calculadora calculadora = new Calculadora();
		Registry reg = null;
		ICalculadora stub = (ICalculadora) UnicastRemoteObject.
				exportObject(calculadora, 1100);
		try {
			System.out.println("Creating registry...");
			reg = LocateRegistry.createRegistry(1099);
		} catch (Exception e) {
			try {
				reg = LocateRegistry.getRegistry(1099);
			} catch (Exception e1) {
				System.exit(0);
			}
		}
		reg.rebind("calculadora", stub);
		System.out.println("Servidor no ar");
	}
}
