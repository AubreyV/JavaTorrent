package org.johnnei.javatorrent.torrent.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.johnnei.javatorrent.network.protocol.IMessage;
import org.johnnei.javatorrent.torrent.protocol.messages.MessageBitfield;
import org.johnnei.javatorrent.torrent.protocol.messages.MessageBlock;
import org.johnnei.javatorrent.torrent.protocol.messages.MessageCancel;
import org.johnnei.javatorrent.torrent.protocol.messages.MessageChoke;
import org.johnnei.javatorrent.torrent.protocol.messages.MessageHave;
import org.johnnei.javatorrent.torrent.protocol.messages.MessageInterested;
import org.johnnei.javatorrent.torrent.protocol.messages.MessageRequest;
import org.johnnei.javatorrent.torrent.protocol.messages.MessageUnchoke;
import org.johnnei.javatorrent.torrent.protocol.messages.MessageUninterested;

/**
 * The factory which maps the message IDs to the {@link IMessage} instances.
 *
 */
public class MessageFactory {

	private final Map<Integer, Supplier<IMessage>> messageSuppliers;

	private MessageFactory(Builder builder) {
		messageSuppliers = builder.messageSuppliers;
	}

	/**
	 * Creates a new message instance which maps to the given ID
	 * @param id The ID of the message
	 * @return New instance of the associated message
	 */
	public IMessage createById(int id) {
		if (!messageSuppliers.containsKey(id)) {
			throw new IllegalArgumentException(String.format("Message %d is not known.", id));
		}

		return messageSuppliers.get(id).get();
	}

	public static class Builder {

		private Map<Integer, Supplier<IMessage>> messageSuppliers;

		public Builder() {
			messageSuppliers = new HashMap<>();

			// Register BitTorrent messages
			registerMessage(BitTorrent.MESSAGE_BITFIELD, () -> new MessageBitfield());
			registerMessage(BitTorrent.MESSAGE_CANCEL, () -> new MessageCancel());
			registerMessage(BitTorrent.MESSAGE_CHOKE, () -> new MessageChoke());
			registerMessage(BitTorrent.MESSAGE_HAVE, () -> new MessageHave());
			registerMessage(BitTorrent.MESSAGE_INTERESTED, () -> new MessageInterested());
			registerMessage(BitTorrent.MESSAGE_PIECE, () -> new MessageBlock());
			registerMessage(BitTorrent.MESSAGE_REQUEST, () -> new MessageRequest());
			registerMessage(BitTorrent.MESSAGE_UNCHOKE, () -> new MessageUnchoke());
			registerMessage(BitTorrent.MESSAGE_UNINTERESTED, () -> new MessageUninterested());
		}

		public Builder registerMessage(int id, Supplier<IMessage> messageSupplier) {
			if (messageSuppliers.containsKey(id)) {
				throw new IllegalStateException(String.format("Failed to add message with id %d: Already taken.", id));
			}

			messageSuppliers.put(id, messageSupplier);
			return this;
		}

		public MessageFactory build() {
			return new MessageFactory(this);
		}

	}

}
