package tardis.Overlord.utils;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.Overlord.aggregators.NodeAggregator;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    /* RELATIVE TO DATA STRUCTURES */

    /* ******** RECORD MAPS ******** */
    public static String mapOfRecordsToString(Map<String, NodeAggregator.MessageRecord> map){
        StringBuilder str = new StringBuilder();
        str.append(map.size());
        for(String s : map.keySet()){
            str.append(";").append(map.get(s).toString());
        }
        return str.toString();
    }

    @SuppressWarnings("unused")
    public static Map<String, NodeAggregator.MessageRecord> mapOfRecordsFromString(String str){
        Map<String, NodeAggregator.MessageRecord> map = new HashMap<>();
        List<String> entries = new ArrayList<>(List.of(str.split(";")));
        int mapSize = Integer.parseInt(entries.get(0));
        entries.remove(0);
        for(String entry : entries){
            NodeAggregator.MessageRecord mr = NodeAggregator.MessageRecord.fromString(entry);
            assert mr != null;
            map.put(mr.getMID(), mr);
        }
        return map;
    }

    /* ***************************** *
     * ********* LONG MAPS ********* *
     * ***************************** */

    @SuppressWarnings("unused")
    public static String mapOfLongsToString(Map<String, Long> map){
        StringBuilder str = new StringBuilder();
        str.append(map.size());
        for(String s : map.keySet()){
            str.append(";").append(s).append(",").append(map.get(s));
        }
        return str.toString();
    }

    @SuppressWarnings("unused")
    public static Map<String, Long> mapOfLongsFromString(String str){
        Map<String, Long> map = new HashMap<>();
        List<String> entries = new ArrayList<>(List.of(str.split(";")));
        int mapSize = Integer.parseInt(entries.get(0));
        entries.remove(0);
        for(String entry : entries){
            List<String> splitEntry = new ArrayList<>(List.of(entry.split(",")));
            String host = splitEntry.get(0);
            long timestamp = Long.parseLong(splitEntry.get(1));
            map.put(host, timestamp);
        }
        return map;
    }

    @SuppressWarnings("unused")
    public static String mapOfUUIDsToString(Map<String, List<UUID>> map){
        StringBuilder str = new StringBuilder();
        str.append(map.size());
        for(String s : map.keySet()){
            str.append("~").append(s).append(";");
            for(UUID u : map.get(s))
                str.append(u.toString()).append(",");
            str.deleteCharAt(str.length()-1);
        }
        return str.toString();
    }

    @SuppressWarnings("unused")
    public static Map<String, List<UUID>> mapOfUUIDsFromString(String s){
        Map<String, List<UUID>> map = new HashMap<>();
        List<String> strings = new ArrayList<>(List.of(s.split("~")));
        int mapSize = Integer.parseInt(strings.get(0));
        strings.remove(0);
        for(String hostEntry : strings){
            List<String> entry = List.of(hostEntry.split(";"));
            String host = entry.get(0);
            map.put(host, new ArrayList<>());
            List<String> msgs = List.of(entry.get(1).split(","));
            for(String msg : msgs) {
                map.get(host).add(UUID.fromString(msg));
            }

        }
        return map;
    }

    /* ********************* *
     * ****** LOGGERS ****** *
     * ********************* */

    @SuppressWarnings("unused")
    public static void logLists(Logger logger, String message, Map<String, List<UUID>> messagesIDs) {
        logger.info(message);
        for (String host : messagesIDs.keySet()) {
            logger.info("   {}:", host);
            for (UUID u : messagesIDs.get(host))
                logger.info("       {}:", u);
        }
    }

    @SuppressWarnings("unused")
    public static void logMap(Logger logger, String message, Map<String, Long> times) {
        logger.info(message);
        for (String msg : times.keySet()) {
            logger.info("   {}:", msg);
            logger.info("   {}:", times.get(msg));
        }
    }

    @SuppressWarnings("unused")
    public static void logRecordMap(Logger logger, String s, Map<String, NodeAggregator.MessageRecord> messageRecords) {
        logger.info(s);
        for(String rec : messageRecords.keySet()){
            logger.info("   - {}", messageRecords.get(rec));
        }
    }

}
