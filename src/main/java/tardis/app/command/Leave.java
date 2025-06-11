package tardis.app.command;

public class Leave extends Command {

	public final static long DEFAULT_RANDOM_SEED = 13051982;
	
	public Leave(String args[]) {
		super(args);
	}
	
	@Override
	public String generateCommand(String[] args) {
		
		return Command.CMD_LEAVE;
	
	}

	public static void main(String[] args) {
		if(args.length != 0) {
			System.err.println("Incorrect argument count\nUsage:java -cp " + System.getProperty("java.class.path") + " java " +
					Leave.class.getCanonicalName());
			System.exit(1);
		}
		
		Leave cmd = new Leave(args);
		cmd.executeCommand(args);
	}

}
 