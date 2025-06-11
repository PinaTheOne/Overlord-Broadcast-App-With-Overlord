package tardis.Overlord.utils;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.data.Host;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
}
