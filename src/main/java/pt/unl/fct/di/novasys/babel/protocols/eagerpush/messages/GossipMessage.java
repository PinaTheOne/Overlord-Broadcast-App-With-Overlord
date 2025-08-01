package pt.unl.fct.di.novasys.babel.protocols.eagerpush.messages;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.messages.IdentifiableProtoMessage;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.BroadcastDelivery;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

public class GossipMessage extends IdentifiableProtoMessage {
    public final static short MSG_CODE = 1601;


	private final Timestamp timestamp;
	private final Host sender;
	private final byte[] payload; 
    private short hopCount;
    private short protoID;
    
    public GossipMessage(Timestamp t, Host s, byte[] p, short pID) {
        super(GossipMessage.MSG_CODE);
       
        this.timestamp = Timestamp.from(t.toInstant());
        this.sender = new Host(s.getAddress(),s.getPort());
        this.payload = p.clone();
        this.hopCount = 0;
        this.protoID = pID;
    }
    
    private GossipMessage(UUID mid, long t, Host s, byte[] p, short h, short pID) {
    	super(GossipMessage.MSG_CODE,mid);
    	this.timestamp = new Timestamp(t);
    	this.sender = s;
    	this.payload = p;
    	this.hopCount = h;
    	this.protoID = pID;
    }
    
    private GossipMessage(GossipMessage m) {
    	super(GossipMessage.MSG_CODE, m.getMID());
    	this.timestamp = m.timestamp;
    	this.sender = m.sender;
    	this.payload = m.payload;
    	this.hopCount = m.hopCount;
    	this.protoID = m.protoID;
    }


	@Override
	public BroadcastDelivery generateDeliveryNotification(short sourceProtoID) {
		return new BroadcastDelivery(sender, payload.clone(), new Timestamp(timestamp.getTime()));
	}
    
    public GossipMessage clone() {
    	return new GossipMessage(this);
    }
        
	public short getHopCount() {
		return hopCount;
	}

	public void setHopCount(short hopCount) {
		this.hopCount = hopCount;
	}

	public short getProtoID() {
		return protoID;
	}

	public void setProtoID(short protoID) {
		this.protoID = protoID;
	}

	public Timestamp getTimestamp() {
		return timestamp;
	}

	public Host getSender() {
		return sender;
	}

	public byte[] getPayload() {
		return payload;
	}
	
	public short incrementHopCount() {
		return ++this.hopCount;
	}

    @Override
    public String toString() {
        return "GosipMessage{" +
                "sender="+sender +
                ", mid=" + getMID().toString() +
                ", timestamp=" + timestamp +
                ", payload size=" + payload.length +
                ", hopCount=" + hopCount +
                ", protoID=" + protoID +
                '}';
    }

   

    public static final ISerializer<GossipMessage> serializer = new ISerializer<GossipMessage>() {
        @Override
        public void serialize(GossipMessage msg, ByteBuf out) throws IOException {
        	out.writeLong(msg.getMID().getMostSignificantBits());
            out.writeLong(msg.getMID().getLeastSignificantBits());
        	Host.serializer.serialize(msg.sender, out);
            out.writeLong(msg.timestamp.getTime());
            out.writeInt(msg.payload.length);
            out.writeBytes(msg.payload);
            out.writeShort(msg.hopCount);
            out.writeShort(msg.protoID);
        }

        @Override
        public GossipMessage deserialize(ByteBuf in) throws IOException {
        	UUID mid = new UUID(in.readLong(), in.readLong());
        	Host origin = Host.serializer.deserialize(in);
            long t = in.readLong();
            byte[] payload = new byte[in.readInt()];
            in.readBytes(payload);
            short h = in.readShort();
            short pid = in.readShort();
            return new GossipMessage(mid, t, origin, payload, h, pid);
            
        }

    };

}
