package tardis.Overlord.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
public class AggregatedStatistics {

    private long start;
    private long end;
    private int msgCount; // TODO: Adicionei isto
    private int nodes;
    private int receivedMessages;
    private int duplicateMessages;
    private double averageRMR;
    private double averageLatency;
    private double globalDuplicationRate;
    private double averageReliability;
    private double averageHops;
    private int nodeCount;
    private int statCount;

    public AggregatedStatistics(long start, long end) {
        this.start = start;
        this.end = end;
        this.nodes = 0;
        this.receivedMessages = 0;
        this.duplicateMessages = 0;
        this.averageHops = 0.0;
        this.averageLatency = 0.0;
        this.averageReliability = 0.0;
        this.statCount = 0;
    }

    public AggregatedStatistics(long start, long end, int nodes,
                                double averageLatency, double averageReliability,
                                double averageHops, double averageRMR,
                                int receivedMessages, int duplicateMessages, int msgCount, int nodeCount) {
        this(start, end);
        this.receivedMessages = receivedMessages;
        this.duplicateMessages = duplicateMessages;
        this.nodes = nodes;
        if (this.receivedMessages != 0) this.globalDuplicationRate = (double) this.duplicateMessages / this.receivedMessages;
        this.averageHops = averageHops;
        this.averageLatency = averageLatency;
        this.averageReliability = averageReliability;
        this.averageRMR = averageRMR;
        this.msgCount = msgCount;
        this.nodeCount = nodeCount;
        this.statCount = 1;
    }

    public AggregatedStatistics(long start, long end, int nodes, double averageLatency, double averageReliability, double averageHops, double averageRMR, int receivedMessages, int duplicateMessages, int msgCount, int nodeCount, int statCount) {
        this(start, end);
        this.receivedMessages = receivedMessages;
        this.duplicateMessages = duplicateMessages;
        this.nodes = nodes;
        if (this.receivedMessages != 0) this.globalDuplicationRate = (double) this.duplicateMessages / this.receivedMessages;
        this.averageHops = averageHops;
        this.averageLatency = averageLatency;
        this.averageReliability = averageReliability;
        this.averageRMR = averageRMR;
        this.msgCount = msgCount;
        this.nodeCount = nodeCount;
        this.statCount = statCount;
    }

    public void addNewStats(AggregatedStatistics stats){
        if(this.start == 0)
            this.start = stats.start;
        else if(stats.start != 0)
            this.start = Math.min(this.start, stats.start);
        this.end = Math.max(this.end, stats.end);
        this.receivedMessages += stats.receivedMessages;
        this.duplicateMessages += stats.duplicateMessages;
        this.nodes = (this.statCount*this.nodes + stats.nodes)/(this.statCount+1);
        this.globalDuplicationRate = (this.statCount*this.globalDuplicationRate + stats.globalDuplicationRate)/(this.statCount + 1);
        this.averageHops = (this.statCount*this.averageHops + stats.averageHops)/(this.statCount + 1);
        this.averageLatency = (this.statCount*this.averageLatency + stats.averageLatency)/(this.statCount + 1);
        this.averageReliability = (this.statCount*this.averageReliability + stats.averageReliability)/(this.statCount + 1);
        this.averageRMR = (this.statCount*this.averageRMR + stats.averageRMR)/(this.statCount + 1);
        this.msgCount += stats.msgCount;
        this.nodeCount = (this.statCount*this.nodeCount + stats.nodeCount)/(this.statCount + 1);
        this.statCount++;
    }

    public int getNodes() {
        return this.nodes;
    }

    public long getStart() {
        return this.start;
    }

    public long getEnd() {
        return this.end;
    }

    public int getReceivedMessages() {
        return receivedMessages;
    }

    public void incrementReceivedMessages(int n) {
        this.receivedMessages += n;
    }

    public int getDuplicateMessages() {
        return duplicateMessages;
    }

    public void incrementDuplicateMessages(int n) {
        this.duplicateMessages += n;
    }

    public double getAverageRMR() {
        return this.averageRMR;
    }

    public double getGlobalDuplicationRate() {
        return globalDuplicationRate;
    }

    public double getAverageLatency() {
        return averageLatency;
    }

    public double getAverageReliability() {
        return averageReliability;
    }

    public double getAverageHops() {
        return this.averageHops;
    }
    public int getMsgCount() { return this.msgCount; }
    public  int getNodeCount(){ return this.nodeCount;}

    public static String toJSON(AggregatedStatistics statistic) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(statistic);
        } catch (JsonProcessingException e) {
            System.err.println(e);
            return "";
        }
    }

    public static byte[] toByteArray(AggregatedStatistics s){
        ByteBuf buf = Unpooled.buffer();
        buf.writeLong(s.start);
        buf.writeLong(s.end);
        buf.writeInt(s.nodes);
        buf.writeDouble(s.averageLatency);
        buf.writeDouble(s.averageReliability);
        buf.writeDouble(s.averageHops);
        buf.writeDouble(s.averageRMR);
        buf.writeInt(s.receivedMessages);
        buf.writeInt(s.duplicateMessages);
        //buf.writeInt(s.sentMessages);
        buf.writeInt(s.msgCount);
        buf.writeInt(s.nodeCount);
        buf.writeInt(s.statCount);
        return buf.array();
    }

    public static AggregatedStatistics deserialize(byte[] s) {
        ByteBuf buf = Unpooled.wrappedBuffer(s);
        long start = buf.readLong();
        long end = buf.readLong();
        int nodes = buf.readInt();
        double averageLatency = buf.readDouble();
        double averageReliability = buf.readDouble();
        double averageHops = buf.readDouble();
        double averageRMR = buf.readDouble();
        int receivedMessages = buf.readInt();
        int duplicateMessages = buf.readInt();
        //int sentMessages = buf.readInt();
        int msgCount = buf.readInt();
        int nodeCount = buf.readInt();
        int statCount = buf.readInt();
        return new AggregatedStatistics(start, end, nodes, averageLatency, averageReliability, averageHops, averageRMR, receivedMessages, duplicateMessages, /*sentMessages,*/ msgCount, nodeCount, statCount);
    }

    @Override
    public String toString() {
        return String.format(
                """
                        BroadcastStatistics {
                          Nodes: %d,
                          Start: %d,
                          End: %d,
                          Received Messages: %d,
                          Duplicate Messages: %d,
                          Average RMR: %.3f,
                          Global Duplication Rate: %.2f%%,
                          """+//Sent Messages: %d,
                          """
                          Average Hops: %.2f,
                          Average Latency: %.2f ms,
                          Average Reliability: %.2f%%,
                          Node Count: %d,
                          Statistics Count: %d}""",
                nodes, start, end, receivedMessages, duplicateMessages, averageRMR, globalDuplicationRate * 100, /*sentMessages,*/ averageHops,
                averageLatency, averageReliability * 100, nodeCount, statCount);
    }

}
