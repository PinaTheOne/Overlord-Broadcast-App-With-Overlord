package tardis.app.command;

public class Marker extends Command {

	public final static long DEFAULT_RANDOM_SEED = 13051982;
	
	public Marker(String args[]) {
		super(args);
	}
	
	@Override
	public String generateCommand(String[] args) {
		
		String label = "Undefined";
		
		if(args[0].equalsIgnoreCase("SRTRW")) {
			label = MARK_SRTRW;
		} else if(args[0].equalsIgnoreCase("ENDRW")) {
			label = MARK_ENDRW;
		} else if(args[0].equalsIgnoreCase("SRTREAD")) {
			label = MARK_SRTREAD;
		} else if(args[0].equalsIgnoreCase("ENDREAD")) {
			label = MARK_ENDREAD;
		} else if(args[0].equalsIgnoreCase("SRTSTABLE")) {
			label = MARK_SRTSTABLE;
		} else if(args[0].equalsIgnoreCase("ENDSTABLE")) {
			label = MARK_ENDSTABLE;
		}
		
		return Command.CMD_MARK + "\n" + label;
	
	}

	public static void main(String[] args) {
		if(args.length != 1) {
			System.err.println("Incorrect argument count\nUsage:java -cp " + System.getProperty("java.class.path") + " java " +
					Marker.class.getCanonicalName() + "SRTRW|ENDRW|SRTREAD|ENDREAD|SRTSTABLE|ENDSTABLE");
			System.exit(1);
		}
		
		Marker cmd = new Marker(args);
		cmd.executeCommand(args);
	}

}
 