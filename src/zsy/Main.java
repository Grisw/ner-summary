package zsy;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

/**
 * Created by MissingNo on 2017/5/16.
 */
public class Main {

    public static void main(String[] args){
        try {
            INameSim nameSim = new NameSim(args[0]);
            LocateRegistry.createRegistry(12138);
            Naming.bind("rmi://localhost:12138/NameSim", nameSim);
        } catch (IOException | AlreadyBoundException e) {
            e.printStackTrace();
        }
    }

}
