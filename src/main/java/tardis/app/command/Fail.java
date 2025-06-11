package tardis.app.command;

public class Fail extends Command {

	public final static long DEFAULT_RANDOM_SEED = 13051982;
	
	public Fail(String args[]) {
		super(args);
	}
	
	@Override
	public String generateCommand(String[] args) {
		
		return Command.CMD_FAIL;
	
	}

	public static void main(String[] args) {
		if(args.length != 0) {
			System.err.println("Incorrect argument count\nUsage:java -cp " + System.getProperty("java.class.path") + " java " +
					Fail.class.getCanonicalName());
			System.exit(1);
		}
		
		Fail cmd = new Fail(args);
		cmd.executeCommand(args);
	}

}
 