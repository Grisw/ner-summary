package zsy;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by MissingNo on 2017/5/17.
 */
public interface INameSim extends Remote {
    String handleText(String text) throws RemoteException;
}
