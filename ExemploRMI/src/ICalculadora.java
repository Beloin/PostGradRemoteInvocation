import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ICalculadora extends Remote{

	public int soma(int a, int b) throws RemoteException;

	public int subtracao(int a, int b) throws RemoteException;

	public int divisao(int a, int b) throws RemoteException;

	public int multiplicacao(int a, int b) throws RemoteException;

	public double avaliar(String expressao) throws RemoteException;
}
