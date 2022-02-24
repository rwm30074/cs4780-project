package cs4780.server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class myftpserver {
	
	public static final int NUMBER_BYTES_READ = 2;
	
    private static int numberOfClients = 0;
    private static int nPortNumber = -1;
    private static int tPortNumber = -1;
	private static CommandIDTable commandIDTable = new CommandIDTable();
	private static CommandCenter cc = new CommandCenter(commandIDTable);
    
    public static void main(String[] args) {
			
		// Check command line arguments and makes sure they are valid
		validateArguments(args);
		
		Thread normalThread = new Thread(() -> {
			ServerSocket ss = null;
			try {
			    ss = new ServerSocket(nPortNumber);
			} catch (IOException e) {
			    System.err.println("Invalid 1st port number! Port number may already be in use");
			    System.exit(1);
			} catch (IllegalArgumentException e) {
			    System.err.println("Invalid 1st port number! Port number is out of range");
			    System.exit(1);
			}
			
			while (true) {
			    System.out.println("Waiting for clients...");
			    Socket toClient = null;
			    try {
			    	toClient = ss.accept();
					numberOfClients++; 
			    } catch (IOException e) {
			    	System.err.println("Error with accepting client socket");
			    }
			    System.out.println("Connection established");
			    System.out.println("Number of clients: " + numberOfClients);
				
			    // Spawn new thread for handling client commands
			    ClientHandler clientThread = null;
			    try {
			    	clientThread = new ClientHandler(toClient, new File(System.getProperty("user.dir")));
			    } catch (IOException e) {
			    	System.err.println("Error with starting client handler");
			    }
			    Thread commandThread = new Thread(clientThread);
			    commandThread.start();
			}
		}, "normalThread");
		
		Thread haltThread = new Thread(() -> {
			ServerSocket terminateServerSocket = null;
			try {
				terminateServerSocket = new ServerSocket(tPortNumber);
			} catch (IOException e) {
			    System.err.println("Invalid 2nd port number! Port number may already be in use");
			    System.exit(1);
			} catch (IllegalArgumentException e) {
			    System.err.println("Invalid 2nd port number! Port number is out of range");
			    System.exit(1);
			}
			
			while (true) {
				Socket toClientTerminate = null;
				try {
					toClientTerminate = terminateServerSocket.accept();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				TerminateHandler terminateWatcher = null;
				
				try {
					terminateWatcher = new TerminateHandler(toClientTerminate);
				} catch (IOException e) {
					System.err.println("Error with starting terminate handler");
				}
			    Thread terminateThread = new Thread(terminateWatcher, "terminateThread");
			    terminateThread.start();
			}
			
		}, "haltThread");
		
		haltThread.start();
		normalThread.start();
	
    } // main
	   
    public static void decrementClientCount() {
    	numberOfClients--;
    } // decrementClientCount	
    
    public static CommandIDTable getCommandIDTable() {
    	return commandIDTable;
    } // getCommandIDTable
    
    private static void validateArguments(String[] args) {
    	if (args.length == 2) {
		    try {
		    	nPortNumber = Integer.parseInt(args[0]);
		    	tPortNumber = Integer.parseInt(args[1]);
		    	if (nPortNumber == tPortNumber) {
		    		System.err.println("Port numbers cannot be the same");
		    		System.exit(1);
		    	}
		    } catch (NumberFormatException e) {
		    	System.err.println("Arguments must be integers");
		    	System.exit(1);
		    }
		} else {
		    System.err.println("Must specify 2 available port numbers, the 1st value being the normal port and the 2nd the terminate port");
		    System.exit(1);
		}
    } // validateArguments
    
    // terminate thread
    static class TerminateHandler implements Runnable {
    	
    	private Socket toClientTerminate;
		private BufferedReader inTerminate;
		private PrintWriter outTerminate;
	
		public TerminateHandler(Socket toClientTerminate) throws IOException {
			this.toClientTerminate = toClientTerminate;
			this.inTerminate = new BufferedReader(new InputStreamReader(toClientTerminate.getInputStream()));
		    this.outTerminate = new PrintWriter(toClientTerminate.getOutputStream(), true);
		}
		
    	public void run() {
		    // loop continues reading commands until the user types in "quit"
			while (true) {
			    String command = "";
			    try {
			    	command = inTerminate.readLine(); // read command
			    } catch (IOException e) {
			    	e.printStackTrace();
			    }	
			    if (command.contains("terminate")) { // if command has 2 parts, the command itself and file
			    	try {
			    		int commandID = Integer.parseInt(inTerminate.readLine()); // fileName
			    		String whatToDo = inTerminate.readLine();
			    		if (whatToDo.equals("update-table")) {
			    			// must be a get command
			    			commandIDTable.terminate(commandID);
			    		} else if (whatToDo.equals("to-server")) {
			    			// must be a put command
			    			if (commandIDTable.contains(commandID) && !commandIDTable.isTerminated(commandID)) {
			    				commandIDTable.terminate(commandID);
			    				outTerminate.println("successful");
			    			} else {
			    				outTerminate.println("Failed to terminate command " + commandID + ". The command has either already ended or does not exist");
			    			}
			    		} else {
			    			commandIDTable.terminate(commandID);
			    		}
			    	} catch (NumberFormatException e) {
			    		outTerminate.println("Invalid input. Specify terminate with an integer command-ID");
			    	} catch (IOException e) {
						e.printStackTrace();
					} 
			    } else if (command.equals("quit")) { // quit
					break;		
			    } else {
			    	outTerminate.println("Unknown command");
			    }
			} // while	 
			try {
				inTerminate.close();
	    		outTerminate.close();
	    		toClientTerminate.close();
			} catch (IOException e) {
				System.err.println("IOException in terminate handler");
			}
			
		} // run
    } // TerminateHandler
    
    // command-reading-thread
    static class ClientHandler implements Runnable {
    	
		private File remoteCurrDir;
		private Socket toClient;
		private DataOutputStream dataOutputStream;
		private DataInputStream dataInputStream;
		private BufferedReader in;
		private PrintWriter out;
			
		public ClientHandler(Socket toClient, File remoteCurrDir) throws IOException {
		    this.remoteCurrDir = remoteCurrDir;
		    this.toClient = toClient;
		    dataOutputStream = new DataOutputStream(toClient.getOutputStream());
		    dataInputStream = new DataInputStream(toClient.getInputStream());
		    in = new BufferedReader(new InputStreamReader(toClient.getInputStream()));
		    out = new PrintWriter(toClient.getOutputStream(), true);
		}
			
		public void run() {
		    try {
		    	// loop continues reading commands until the user types in "quit"
				while (true) {
				    String command = "";
				    try {
				    	command = in.readLine();
				    } catch (IOException e) {
				    	e.printStackTrace();
				    }	
				    if (command.contains(" ") && !command.substring(command.indexOf(" ") + 1).equals("")) { // if command has 2 parts, the command itself and file
				    	String fileName = command.substring(command.indexOf(" ") + 1); // fileName
						if (command.substring(0, command.indexOf(" ")).equals("get")) { // get command
							cc.handleGet(command, dataOutputStream, out, remoteCurrDir);
						} else if (command.substring(0, command.indexOf(" ")).equals("put")) { // put command
							cc.handlePut(command, dataInputStream, out, remoteCurrDir);		
						} else if (command.substring(0, command.indexOf(" ")).equals("delete")) { // delete command
						    cc.handleDelete(fileName, out, remoteCurrDir);									
						} else if (command.substring(0, command.indexOf(" ")).equals("cd")) { // cd command
						    remoteCurrDir = cc.handleCd(fileName, out, remoteCurrDir);											
						} else if (command.substring(0, command.indexOf(" ")).equals("mkdir")) { // mkdir
						    cc.handleMkdir(fileName, out, remoteCurrDir);
						} else {
						    out.println("Unknown command");
						}		
				    } else if (command.equals("ls")) { // ls
						cc.handleLs(out, remoteCurrDir);	
				    } else if (command.equals("pwd")) { // pwd
						out.println(remoteCurrDir.getAbsolutePath());
				    } else if (command.equals("quit")) { // quit
						break;		
				    } else {
				    	out.println("Unknown command");
				    }
				} // while
				cc.handleQuit(toClient, in, out);
		    } catch (IOException e) {
		    	System.err.println("IOException in client handler");
		    } 
		} // run
	} // ClientHandler class
	
}