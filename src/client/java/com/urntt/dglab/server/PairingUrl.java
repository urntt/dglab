package com.urntt.dglab.server;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import com.urntt.dglab.config.DgLabConfig;

/**
 * Builds the text of the pairing QR code. The DG-LAB app only accepts this exact form: its download page, the tag
 * {@code DGLAB-SOCKET}, and the WebSocket address ending in the terminal ID, separated by two {@code #}.
 */
public final class PairingUrl {
	private static final String PREFIX = "https://www.dungeon-lab.com/app-download.php#DGLAB-SOCKET#";

	private PairingUrl() {
	}

	public static String of(final String host, final int port, final String terminalId) {
		String urlHost = host.contains(":") ? "[" + host + "]" : host;
		return PREFIX + "ws://" + urlHost + ":" + port + "/" + terminalId;
	}

	/**
	 * Builds the text for the configured address and port, falling back to this computer's LAN address and the
	 * port the server listens on.
	 */
	public static String of(final DgLabConfig config, final String terminalId) {
		return of(host(config), port(config), terminalId);
	}

	/** The host the QR code points the app to. */
	public static String host(final DgLabConfig config) {
		String address = config.qrAddress();
		return address.equals(DgLabConfig.QR_ADDRESS_AUTOMATIC) ? LanAddresses.preferred() : address;
	}

	/** The port the QR code points the app to. */
	public static int port(final DgLabConfig config) {
		return config.qrPort() == DgLabConfig.QR_PORT_SAME_AS_LISTEN ? config.listenPort() : config.qrPort();
	}

	/**
	 * Returns whether {@code host} is an IP address or a domain name.
	 */
	public static boolean isValidHost(final String host) {
		return InetAddresses.isInetAddress(host) || InternetDomainName.isValid(host);
	}
}
