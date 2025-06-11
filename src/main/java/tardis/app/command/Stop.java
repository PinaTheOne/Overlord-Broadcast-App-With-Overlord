package tardis.app.command;

public class Stop extends Command {

	public final static long DEFAULT_RANDOM_SEED = 13051982;
	
	public Stop(String args[]) {
		super(args);
	}
	
	@Override
	public String generateCommand(String[] args) {
		
		return Command.CMD_STOP;
	
	}

	public static void main(String[] args) {
		if(args.length != 0) {
			System.err.println("Incorrect argument count\nUsage:java -cp " + System.getProperty("java.class.path") + " java " +
					Stop.class.getCanonicalName());
			System.exit(1);
		}
		
		Stop cmd = new Stop(args);
		cmd.executeCommand(args);
	}

}
 