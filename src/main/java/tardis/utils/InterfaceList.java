package tardis.utils;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class InterfaceList {

	public InterfaceList() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) throws SocketException {
	    Enumeration<NetworkInterface> networkInterfaces =  NetworkInterface.getNetworkInterfaces();

	    while(networkInterfaces.hasMoreElements())
	    {
	        NetworkInterface networkInterface = networkInterfaces.nextElement();

	        if (networkInterface.isUp())
	            System.out.println("Display name: " + networkInterface.getDisplayName() + "\nName: " + networkInterface.getName() + "\nAddress: " + networkInterface.getInterfaceAddresses().get(0));
	    }
	}

}
