package tardis.management;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ChangeBroadcastFanoutMessage {

    public static long TAG = 12312;

    private String sender;
    private int newFanout;

    public ChangeBroadcastFanoutMessage(String sender, int newFanout) {
        this.sender = sender;
        this.newFanout = newFanout;
    }

    public String getSender() {
		return sender;
	}

    public int getNewFanout() {
        return this.newFanout;
    }

    public byte[] toByteArray() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);

        dos.writeLong(TAG);
		dos.writeUTF(this.sender);
        dos.writeInt(this.newFanout);

		return baos.toByteArray();
	}

	public static ChangeBroadcastFanoutMessage fromByteArray(byte[] uMsg) throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(uMsg);
		DataInputStream dis = new DataInputStream(bais);

        long tag = dis.readLong();

        if (tag != TAG) throw new IOException();

		String sender = dis.readUTF();
        int newFanout = dis.readInt();

        return new ChangeBroadcastFanoutMessage(sender, newFanout);
	}

}
