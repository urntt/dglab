package com.urntt.dglab.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.Optional;

/**
 * The JSON envelope of the DG-LAB app's socket control protocol. Every message carries all four fields.
 *
 * @param type one of the {@code TYPE_} constants
 * @param clientId the ID of the controlling terminal, which is this mod
 * @param targetId the ID of the app
 * @param message a command, a status code, or a report, depending on {@code type}
 */
public record SocketMessage(String type, String clientId, String targetId, String message) {
	public static final String TYPE_BIND = "bind";
	public static final String TYPE_MSG = "msg";
	public static final String TYPE_HEARTBEAT = "heartbeat";
	public static final String TYPE_BREAK = "break";
	public static final String TYPE_ERROR = "error";

	/** Sent with {@link #TYPE_BIND} when a connection is told its own ID. */
	public static final String MESSAGE_ASSIGN_ID = "targetId";
	/** Sent by the app with {@link #TYPE_BIND} to ask for pairing with the terminal whose QR code it scanned. */
	public static final String MESSAGE_BIND_REQUEST = "DGLAB";

	public static final String CODE_OK = "200";
	public static final String CODE_PEER_DISCONNECTED = "209";
	public static final String CODE_NO_SUCH_TARGET = "401";
	public static final String CODE_NOT_BOUND = "402";
	public static final String CODE_NOT_JSON = "403";
	public static final String CODE_MESSAGE_TOO_LONG = "405";

	/** The app drops messages whose JSON text is longer than this. */
	public static final int MAX_LENGTH = 1950;

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public String toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("type", this.type);
		json.addProperty("clientId", this.clientId);
		json.addProperty("targetId", this.targetId);
		json.addProperty("message", this.message);
		return GSON.toJson(json);
	}

	/**
	 * Parses a message, or returns an empty result if {@code text} is not a JSON object with the four string fields.
	 */
	public static Optional<SocketMessage> parse(final String text) {
		try {
			JsonElement element = GSON.fromJson(text, JsonElement.class);
			if (element == null || !element.isJsonObject()) {
				return Optional.empty();
			}
			JsonObject json = element.getAsJsonObject();
			String type = string(json, "type");
			String clientId = string(json, "clientId");
			String targetId = string(json, "targetId");
			String message = string(json, "message");
			if (type == null || clientId == null || targetId == null || message == null) {
				return Optional.empty();
			}
			return Optional.of(new SocketMessage(type, clientId, targetId, message));
		} catch (JsonParseException e) {
			return Optional.empty();
		}
	}

	private static String string(final JsonObject json, final String name) {
		JsonElement value = json.get(name);
		if (value == null || !value.isJsonPrimitive()) {
			return null;
		}
		return value.getAsString();
	}
}
