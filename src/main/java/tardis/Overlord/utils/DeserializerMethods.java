package tardis.Overlord.utils;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class DeserializerMethods {

    public static UUID deserializeUUID(ByteBuf in){
        long highBytes = in.readLong();
        long lowBytes = in.readLong();
        return new UUID(highBytes, lowBytes);
    }

    public static String deserializeString(ByteBuf in) {
        byte[] stringBytes = new byte[in.readInt()];
        in.readBytes(stringBytes);
        return new String(stringBytes, StandardCharsets.UTF_8);
    }

    public static String deserializeString(DataInputStream in) throws IOException {
        byte[] stringBytes = new byte[in.readInt()];
        in.read(stringBytes);
        return new String(stringBytes, StandardCharsets.UTF_8);
    }

    public static Host deserializeHost(ByteBuf in) throws UnknownHostException {
        String ip = deserializeString(in);
        int port = in.readInt();
        return new Host(InetAddress.getByName(ip), port);
    }

    public static Host deserializeHost(DataInputStream in) throws IOException {
        String ip = deserializeString(in);
        int port = in.readInt();
        return new Host(InetAddress.getByName(ip), port);
    }

    public static byte[] deserializeByteArray(ByteBuf in){
        byte[] stringBytes = new byte[in.readInt()];
        in.readBytes(stringBytes);
        return stringBytes;
    }

    public static long deserializeLong(ByteBuf buf) {
        return buf.readLong();
    }
}
