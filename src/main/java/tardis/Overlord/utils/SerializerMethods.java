package tardis.Overlord.utils;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressWarnings("unused")
public class SerializerMethods {

    public static void serializeUUID(UUID id, ByteBuf out){
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    public static void serializeString(String str, ByteBuf out){
        byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
        out.writeInt(strBytes.length);
        out.writeBytes(strBytes);
    }

    public static void serializeByteArray(byte[] arr, ByteBuf out){
        out.writeInt(arr.length);
        out.writeBytes(arr);
    }

    public static void serializeHost(Host host, ByteBuf out) {
        String ip = host.getAddress().getHostAddress();
        int port = host.getPort();
        serializeString(ip, out);
        out.writeInt(port);
    }

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

    // TODO: Check
    @SuppressWarnings("ResultOfMethodCallIgnored")
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
