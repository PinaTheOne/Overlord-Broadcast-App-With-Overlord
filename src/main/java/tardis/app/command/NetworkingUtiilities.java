package tardis.app.command;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class NetworkingUtiilities {
	/*
	 * Returns the ipv4 address of the given interface
	 * 
	 * @param inter name of the interface
	 * 
	 * @return ipv4 address of the interface
	 * 
	 * @throws SocketException if the interface does not exist or does not have an
	 * ipv4 address
	 */
	public static String getAddress(String inter) throws SocketException {
		NetworkInterface byName = NetworkInterface.getByName(inter);
		if (byName == null) {
			return null;
		}
		Enumeration<InetAddress> addresses = byName.getInetAddresses();
		InetAddress currentAddress;
		while (addresses.hasMoreElements()) {
			currentAddress = addresses.nextElement();
			if (currentAddress instanceof Inet4Address)
				return currentAddress.getHostAddress();
		}
		return null;
	}
}
