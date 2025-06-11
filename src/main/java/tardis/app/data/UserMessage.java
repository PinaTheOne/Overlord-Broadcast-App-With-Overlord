package tardis.app.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class UserMessage {

	public final static String PATTERN_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

	private final long timestamp;
	private final String sender;
	private final String alias;
	private final String message;
	private final String attachmentName;
	private final byte[] attach;

	public UserMessage(String sender, String alias, String msg) {
		this.timestamp = System.currentTimeMillis();
		this.sender = sender;
		this.alias = alias;
		this.message = msg;
		this.attachmentName = null;
		this.attach = null;
	}

	public UserMessage(long ts, String sender, String alias, String msg) {
		this.timestamp = ts;
		this.sender = sender;
		this.alias = alias;
		this.message = msg;
		this.attachmentName = null;
		this.attach = null;
	}

	public UserMessage(String sender, String alias, String msg, File f) throws IOException {
		this.timestamp = System.currentTimeMillis();
		this.sender = sender;
		this.alias = alias;
		this.message = msg;
		this.attachmentName = f.getName();
		this.attach = Files.readAllBytes(f.toPath());
	}

	public UserMessage(String sender, String alias, String msg, String filename, byte[] data) {
		this.timestamp = System.currentTimeMillis();
		this.sender = sender;
		this.alias = alias;
		this.message = msg;
		this.attachmentName = filename;
		this.attach = data;
	}

	public UserMessage(long ts, String sender, String alias, String msg, String filename, byte[] data) {
		this.timestamp = ts;
		this.sender = sender;
		this.alias = alias;
		this.message = msg;
		this.attachmentName = filename;
		this.attach = data;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getTimeStampRepresentation() {
		return DateTimeFormatter.ofPattern(PATTERN_FORMAT).withZone(ZoneId.systemDefault())
				.format(Instant.ofEpochMilli(timestamp));
	}

	public String getSender() {
		return sender;
	}

	public String getAlias() {
		return alias;
	}

	public String getMessage() {
		return message;
	}

	public String getMessage(int previewLen) {
		if(previewLen < message.length())
			return message.substring(0, previewLen);
		else 
			return message;
	}
	
	public boolean hasAttachment() {
		return this.attachmentName != null;
	}
	
	public String getAttachmentName() {
		return attachmentName;
	}

	public byte[] getAttach() {
		return attach;
	}

	public byte[] toByteArray() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);

		dos.writeLong(timestamp);
		dos.writeUTF(sender);
		dos.writeUTF(alias);
		dos.writeUTF(message);

		if (attachmentName != null) {
			dos.writeBoolean(true);
			dos.writeUTF(attachmentName);
			dos.writeInt(attach.length);
			dos.write(attach);
		} else {
			dos.writeBoolean(false);
		}

		return baos.toByteArray();
	}

	public static UserMessage fromByteArray(byte[] uMsg) throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(uMsg);
		DataInputStream dis = new DataInputStream(bais);

		long ts = dis.readLong();
		String sender = dis.readUTF();
		String alias = dis.readUTF();
		String message = dis.readUTF();

		if (dis.readBoolean()) {
			String attachName = dis.readUTF();
			byte[] data = new byte[dis.readInt()];
			dis.read(data);
			return new UserMessage(ts, sender, alias, message, attachName, data);
		} else {
			// no attachment
			return new UserMessage(ts, sender, alias, message);
		}

	}

	public int getAttachSize() {
		if(this.attach == null)
			return 0;
		return this.attach.length;
	}

}
