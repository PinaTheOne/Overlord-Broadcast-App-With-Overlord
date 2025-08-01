package tardis.Overlord.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import pt.unl.fct.di.novasys.babel.metrics.NodeSample;
import tardis.Overlord.utils.aggregators.NodeAggregator.MessageRecord;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class DataStructSerializer {

    /* ********************* *
     * **** SERIALIZERS **** *
     * ********************* */

    /* ***************************** *
     * ******** SAMPLE MAPS ******** *
     * ***************************** */

    public static byte[] serializeSampleMap(Map<String, NodeSample> aggregatedData) {
        ByteBuf out = Unpooled.buffer();
        out.writeInt(aggregatedData.size());
        for(String h : aggregatedData.keySet()){
            byte[] strBytes = h.getBytes(StandardCharsets.UTF_8);
            out.writeInt(strBytes.length);
            out.writeBytes(strBytes);
            byte[] arr = aggregatedData.get(h).toByteArray();
            out.writeInt(arr.length);
            out.writeBytes(arr);
        }
        return out.array();
    }

    public static Map<String, NodeSample> deserializeSampleMap(byte[] b) {
        ByteBuf in = Unpooled.wrappedBuffer(b);
        Map<String, NodeSample> map = new HashMap<>();
        int size = in.readInt();
        for(int i = 0; i < size; i++){
            byte[] stringBytes = new byte[in.readInt()];
            in.readBytes(stringBytes);
            String h    = new String(stringBytes, StandardCharsets.UTF_8);
            byte[] bytes = new byte[in.readInt()];
            in.readBytes(bytes);
            NodeSample data = NodeSample.fromByteArray(bytes);
            map.put(h, data);
        }
        return map;
    }

    /* ***************************** *
     * ******** RECORD MAPS ******** *
     * ***************************** */
    public static String mapOfRecordsToString(Map<String, MessageRecord> map){
        StringBuilder str = new StringBuilder();
        str.append(map.size());
        for(String s : map.keySet()){
            str.append(";").append(map.get(s).toString());
        }
        return str.toString();
    }

    @SuppressWarnings("unused")
    public static Map<String, MessageRecord> mapOfRecordsFromString(String str){
        Map<String, MessageRecord> map = new HashMap<>();
        List<String> entries = new ArrayList<>(List.of(str.split(";")));
        int mapSize = Integer.parseInt(entries.get(0));
        entries.remove(0);
        for(String entry : entries){
            MessageRecord mr = MessageRecord.fromString(entry);
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
    public static void logRecordMap(Logger logger, String s, Map<String, MessageRecord> messageRecords) {
        logger.info(s);
        for(String rec : messageRecords.keySet()){
            logger.info("   - {}", messageRecords.get(rec));
        }
    }

}
