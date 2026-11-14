package com.urntt.dglab.server;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * Finds the addresses under which a phone on the same network can reach this computer.
 */
public final class LanAddresses {
	/** The address used when no network interface is up; it only works for an app on this computer. */
	public static final String LOOPBACK = "127.0.0.1";

	/**
	 * An IPv4 address of this computer.
	 *
	 * @param address the address in dotted form
	 * @param interfaceName the name the operating system shows for its network interface
	 * @param siteLocal whether the address belongs to a private network, as home and office networks do
	 */
	public record Candidate(String address, String interfaceName, boolean siteLocal) {
	}

	private LanAddresses() {
	}

	/**
	 * Lists the IPv4 addresses of all network interfaces that are up, private-network addresses first.
	 */
	public static List<Candidate> list() {
		List<Candidate> candidates = new ArrayList<>();
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces != null && interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();
				if (!networkInterface.isUp() || networkInterface.isLoopback()) {
					continue;
				}
				Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
				while (addresses.hasMoreElements()) {
					if (addresses.nextElement() instanceof Inet4Address address && !address.isLinkLocalAddress()) {
						candidates.add(new Candidate(address.getHostAddress(), networkInterface.getDisplayName(), address.isSiteLocalAddress()));
					}
				}
			}
		} catch (SocketException e) {
			// Without the interface list there is nothing to offer; the caller falls back to the loopback address.
		}
		candidates.sort(Comparator.comparing((Candidate candidate) -> !candidate.siteLocal()));
		return candidates;
	}

	/**
	 * Returns the address the pairing QR code uses when none is configured.
	 */
	public static String preferred() {
		List<Candidate> candidates = list();
		return candidates.isEmpty() ? LOOPBACK : candidates.getFirst().address();
	}
}
