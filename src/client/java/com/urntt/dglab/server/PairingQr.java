package com.urntt.dglab.server;

import io.nayuki.qrcodegen.QrCode;

/**
 * Encodes the pairing text as a QR code.
 */
public final class PairingQr {
	/** The blank border, in modules, the QR code standard requires around a code. */
	public static final int QUIET_ZONE = 4;

	private PairingQr() {
	}

	/**
	 * Returns the code's modules by row and column; {@code true} is a dark module. The quiet zone is not included.
	 */
	public static boolean[][] encode(final String text) {
		// Medium error correction keeps the code readable off a screen without making it much denser.
		QrCode code = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
		boolean[][] modules = new boolean[code.size][code.size];
		for (int y = 0; y < code.size; y++) {
			for (int x = 0; x < code.size; x++) {
				modules[y][x] = code.getModule(x, y);
			}
		}
		return modules;
	}
}
