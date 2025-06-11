package tardis.Overlord.utils;

import java.util.HashSet;
import java.util.UUID;

import pt.unl.fct.di.novasys.network.data.Host;

public class MessageStatistics implements Comparable<MessageStatistics> {

    private final UUID msgID;
    private final long creationTime;
    private long latency;
    private int highestHop;
    private float reliability;
    private final HashSet<Host> delivered;
    private int receiveCount;

    public MessageStatistics(UUID mid, long creationTime, long deliveryTime, int hopCount, Host receiver) {
        this.msgID = mid;
        this.creationTime = creationTime;
        this.latency = deliveryTime - creationTime;
        this.highestHop = hopCount;
        this.reliability = 0;
        this.delivered = new HashSet<Host>();
        this.delivered.add(receiver);
        this.receiveCount = 1;
    }

    public void updateStatistics(long deliveryTime, int hopCount, Host receiver) {
        if (this.delivered.add(receiver)) {
            long latency = deliveryTime - creationTime;
            if (latency > this.latency)
                this.latency = latency;
            if (hopCount > this.highestHop)
                this.highestHop = hopCount;
        }
        this.receiveCount++;
    }

    @Override
    public String toString() {
        return String.format("{id=%s,created=%d,delivered=%d,latency=%d,hop=%d,reliability=%f}", msgID, creationTime,
                delivered.size(), latency, highestHop, reliability);
    }

    public UUID getMsgID() {
        return this.msgID;
    }

    public int getDeliveryCount() {
        return this.delivered.size();
    }

    public long getLatency() {
        return latency;
    }

    public int getHighestHop() {
        return highestHop;
    }

    public long getCreationTime() {
        return this.creationTime;
    }

    public void computeReliability(int nodeCount) {
        this.reliability = ((float) this.delivered.size()) / nodeCount;
        if (this.reliability > 1.0)
            this.reliability = (float) 1.0;
    }

    public float getReliability() {
        return this.reliability;
    }

    public int getReceiveCount() {
        return this.receiveCount;
    }

    @Override
    public int compareTo(MessageStatistics o) {
        return Long.compare(this.creationTime, o.creationTime);
    }

}
