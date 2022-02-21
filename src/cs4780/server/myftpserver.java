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
	private static boolean keepGoing = false;
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
				System.out.println("Waiting for terminate command...");
				Socket toClientTerminate = null;
				try {
					toClientTerminate = terminateServerSocket.accept();
				} catch (IOException e) {
					// TODO Auto-generated catch block
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
    }
    
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
    }
    
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
			    				outTerminate.println("Successfully terminated command " + commandID);
			    			} else {
			    				outTerminate.println("Failed to terminate command " + commandID + ". The command has either already ended or does not exist");
			    			}
			    		} else {
			    			commandIDTable.terminate(commandID);
			    		}
			    	} catch (NumberFormatException e) {
			    		outTerminate.println("Invalid input. Specify terminate with an integer command-ID");
			    	} catch (IOException e) {
						// TODO Auto-generated catch block
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
    }
    
    // command-reading-thread
    static class ClientHandler implements Runnable {
    	
    	//private static final int NUMBER_BYTES_READ = 1;
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
				    	System.out.println("Command is " + command);
				    } catch (IOException e) {
				    	e.printStackTrace();
				    }	
				    if (command.contains(" ") && !command.substring(command.indexOf(" ") + 1).equals("")) { // if command has 2 parts, the command itself and file
				    	String fileName = command.substring(command.indexOf(" ") + 1); // fileName
						if (command.substring(0, command.indexOf(" ")).equals("get")) { // get command
							cc.handleGet(command, dataOutputStream, out, remoteCurrDir);
							//handleGet(command, dataOutputStream, out);
						} else if (command.substring(0, command.indexOf(" ")).equals("put")) { // put command
							cc.handlePut(command, dataInputStream, out, remoteCurrDir);
								//handlePut(command, dataInputStream);			
						} else if (command.substring(0, command.indexOf(" ")).equals("delete")) { // delete command
						    handleDelete(fileName);									
						} else if (command.substring(0, command.indexOf(" ")).equals("cd")) { // cd command
						    handleCd(fileName);											
						} else if (command.substring(0, command.indexOf(" ")).equals("mkdir")) { // mkdir
						    handleMkdir(fileName);
						} else {
						    out.println("Unknown command");
						}		
				    } else if (command.equals("ls")) { // ls
						handleLs();	
				    } else if (command.equals("pwd")) { // pwd
						out.println(remoteCurrDir.getAbsolutePath());
				    } else if (command.equals("quit")) { // quit
						break;		
				    } else {
				    	out.println("Unknown command");
				    }
				} // while
				handleQuit();
		    } catch (IOException e) {
		    	System.err.println("IOException in client handler");
		    } 
		} // run
		
		/********** Commands ***********/
		
		private void handleDelete(String fileName) {
			File toBeDeletedFile = new File(remoteCurrDir.getAbsolutePath() + "/" + fileName);
		    if (toBeDeletedFile.exists() && toBeDeletedFile.isFile()) {
				if (toBeDeletedFile.delete()) {
				    out.println("successful");
				} else {
				    out.println("Unsuccessful delete");
				}
			} else {
				out.println("File either does not exist or is not considered a file");
		    }
		} // handleDelete
		
		private void handleCd(String fileName) {
			boolean hasException = false;
		    File returnFile = new File(remoteCurrDir.getAbsolutePath()); // a backup if the command fails
		    String potPath = remoteCurrDir.getAbsolutePath() + "/" + fileName;
		    File potDirectory = new File(potPath);
		    if (potDirectory.exists() && potDirectory.isDirectory()) {
				File tempFile = null;
				String desPath = fileName;
				if (desPath.indexOf(desPath.length() - 1) == '/') { // get rid of end backslash if there is one
				    desPath.substring(0, desPath.length() - 1);
				}
				if (desPath.indexOf(desPath.length() - 1) == '.' && desPath.indexOf(desPath.length() - 2) == '/') { // get rid of . if it is the last character
				    desPath.substring(0, desPath.length() - 2);
				}
				if (desPath.length() == 1 && desPath.charAt(0) == '.') { // if . is the only character, then get rid of it
				    desPath = "";
				}
				while (!desPath.equals("")) {
				    String part = ""; // read up to the first / and go to that folder
				    if (desPath.contains("/")) {
						part = desPath.substring(0, desPath.indexOf("/")); // part of address, which is the leftmost directory in the path
						desPath = desPath.substring(desPath.indexOf("/") + 1); // remove that part from the address and continue with the remaining address
				    } else {
						part = desPath;
						desPath = "";
				    }
				    if (!part.equals(".")) {
						if (part.equals("..")) {
						    try {
						    	remoteCurrDir = remoteCurrDir.getParentFile();
						    } catch (NullPointerException e) {
								remoteCurrDir = returnFile;
								out.println("No such parent folder exists");
								hasException = true;
								break;
						    }
						} else {
						    tempFile = new File(remoteCurrDir.getAbsolutePath() + "/" + part);
						    remoteCurrDir = tempFile;
						}
				    }
				}
				if (hasException == false) {
				    out.println("successful");
				}
		    } else {
		    	out.println("No such directory exists");
		    }
		} // handleCd
		
		private void handleMkdir(String fileName) {
			File newDir = new File(remoteCurrDir.toURI().getPath() + fileName);
		    if (!newDir.exists()){
				newDir.mkdirs();
				out.println("successful");
			} else {
				out.println("Directory already exists");
			}
		} // handleMkdir
		
		private void handleLs() {
			File[] listOfFiles = remoteCurrDir.listFiles();
			String fileNames = "";
			for (int i = 0; i < listOfFiles.length; i++) {
			    fileNames = fileNames + listOfFiles[i].getName() + "  ";
			}
			out.println(fileNames);
		} // handleLs
		
		private void handleQuit() throws IOException {
			myftpserver.decrementClientCount();
			in.close();
			out.close();
			toClient.close();
		}
		
	} // ClientHandler class
	
}