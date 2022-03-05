package cs4780.server;

import java.util.HashMap;

public class CommandIDTable {
	
	private HashMap<Integer, Boolean> table; // pairs commandID with boolean that shows whether commandID has been terminated or not
	private int currentCommandID;

	public CommandIDTable() {
		table = new HashMap<Integer, Boolean>();
		currentCommandID = 1;
	}
	
	public synchronized int getCurrentCommandID() {
		return currentCommandID;
	}
	
	public synchronized void addToTable(int commandID, boolean isTerminated) {
		table.put(commandID, isTerminated);
	}
	
	public synchronized void terminate(int commandID) {
		table.put(commandID, true);
	}
	
	public synchronized void incrementCurrentCommandID() {
		currentCommandID++;
	}
	
	public synchronized void completedCommand(int commandID) {
		table.remove(commandID);
	}
	
	public synchronized boolean isTerminated(int commandID) {
		if (contains(commandID) == false) {
			return false;
		}
		return table.get(commandID);
	}
	
	public synchronized boolean contains(int commandID) {
		return table.containsKey(commandID);
	}
	
}