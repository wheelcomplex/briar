package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.lang.System.arraycopy;
import static org.briarproject.bramble.api.sync.Message.FORMAT_VERSION;
import static org.briarproject.bramble.api.sync.MessageId.BLOCK_LABEL;
import static org.briarproject.bramble.api.sync.MessageId.ID_LABEL;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.INT_64_BYTES;
import static org.briarproject.bramble.util.ByteUtils.readUint64;
import static org.briarproject.bramble.util.ByteUtils.writeUint64;

@Immutable
@NotNullByDefault
class MessageFactoryImpl implements MessageFactory {

	private static final byte[] FORMAT_VERSION_BYTES =
			new byte[] {FORMAT_VERSION};

	private final CryptoComponent crypto;

	@Inject
	MessageFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public Message createMessage(GroupId g, long timestamp, byte[] body) {
		if (body.length == 0) throw new IllegalArgumentException();
		if (body.length > MAX_MESSAGE_BODY_LENGTH)
			throw new IllegalArgumentException();
		MessageId id = getMessageId(g, timestamp, body);
		return new Message(id, g, timestamp, body);
	}

	private MessageId getMessageId(GroupId g, long timestamp, byte[] body) {
		// There's only one block, so the root hash is the hash of the block
		byte[] rootHash = crypto.hash(BLOCK_LABEL, FORMAT_VERSION_BYTES, body);
		byte[] timeBytes = new byte[INT_64_BYTES];
		writeUint64(timestamp, timeBytes, 0);
		byte[] idHash = crypto.hash(ID_LABEL, FORMAT_VERSION_BYTES,
				g.getBytes(), timeBytes, rootHash);
		return new MessageId(idHash);
	}

	@Override
	public Message createMessage(byte[] raw) {
		if (raw.length <= MESSAGE_HEADER_LENGTH)
			throw new IllegalArgumentException();
		if (raw.length > MAX_MESSAGE_LENGTH)
			throw new IllegalArgumentException();
		byte[] groupId = new byte[UniqueId.LENGTH];
		arraycopy(raw, 0, groupId, 0, UniqueId.LENGTH);
		GroupId g = new GroupId(groupId);
		long timestamp = readUint64(raw, UniqueId.LENGTH);
		byte[] body = new byte[raw.length - MESSAGE_HEADER_LENGTH];
		arraycopy(raw, MESSAGE_HEADER_LENGTH, body, 0, body.length);
		MessageId id = getMessageId(g, timestamp, body);
		return new Message(id, g, timestamp, body);
	}

	@Override
	public byte[] getRawMessage(Message m) {
		byte[] body = m.getBody();
		byte[] raw = new byte[MESSAGE_HEADER_LENGTH + body.length];
		arraycopy(m.getGroupId().getBytes(), 0, raw, 0, UniqueId.LENGTH);
		writeUint64(m.getTimestamp(), raw, UniqueId.LENGTH);
		arraycopy(body, 0, raw, MESSAGE_HEADER_LENGTH, body.length);
		return raw;
	}
}
