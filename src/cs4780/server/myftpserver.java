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
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class myftpserver {
	
    private static int numberOfClients = 0;
    private static int nPortNumber = -1;
    private static int tPortNumber = -1;
	
    public static void main(String[] args) {
			
		// Check command line arguments and makes sure they are valid
		validateArguments(args);
	
		ServerSequence serverSeq = new ServerSequence(nPortNumber, tPortNumber);
		Thread t1 = new Thread(() -> {
			serverSeq.normalSequence();
		});
		
		Thread t2 = new Thread(() -> {
			serverSeq.terminateSequence();
		});
		
		t1.start();
		t2.start();
	
    } // main
	   
    public static void decrementClientCount() {
    	numberOfClients--;
    } // decrementClientCount	
    
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
    
    // class for coordinating normal thread and terminate thread
    private static class ServerSequence {
    	private boolean keepGoingNormal;
    	int nPortNumber;
    	int tPortNumber;
    	
    	public ServerSequence(int nPortNumber, int tPortNumber) {
    		keepGoingNormal = false;
    		this.nPortNumber = nPortNumber;
    		this.tPortNumber = tPortNumber;
    	}
    	
    	public synchronized void normalSequence() {
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
	
			// make it so it waits for the 2nd thread to validate the terminate socket before proceeding to while loop
			while (!keepGoingNormal) {
				try {
					wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
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
				clientThread = new ClientHandler(toClient);
			    } catch (IOException e) {
				System.err.println("Error with starting client handler");
			    }
			    new Thread(clientThread).start();
			}
    	}
    	
    	public synchronized void terminateSequence() {
    		ServerSocket terminateSocket = null;
			try {
				terminateSocket = new ServerSocket(tPortNumber);
			} catch (IOException e) {
			    System.err.println("Invalid 2nd port number! Port number may already be in use");
			    System.exit(1);
			} catch (IllegalArgumentException e) {
			    System.err.println("Invalid 2nd port number! Port number is out of range");
			    System.exit(1);
			}
			
			keepGoingNormal = true;
			notify();
    	}
    }
    
    // command-reading-thread
    private static class ClientHandler implements Runnable {
    	
		private File remoteCurrDir;
		private Socket toClient;
		private DataOutputStream dataOutputStream;
		private DataInputStream dataInputStream;
		private BufferedReader in;
		private PrintWriter out;
		private File returnFile;
			
		public ClientHandler(Socket toClient) throws IOException {
		    remoteCurrDir =  new File(System.getProperty("user.dir"));
		    this.toClient = toClient;
		    dataOutputStream = new DataOutputStream(toClient.getOutputStream());
		    dataInputStream = new DataInputStream(toClient.getInputStream());
		    in = new BufferedReader(new InputStreamReader(toClient.getInputStream()));
		    out = new PrintWriter(toClient.getOutputStream(), true);
		    returnFile = null;
		}
			
		public void run() {
		    try {
			// loop continues reading commands until the user types in "quit"
			while (true) {
			    String command = "";
			    try {
			    	command = in.readLine(); // read command
			    } catch (IOException e) {
			    	e.printStackTrace();
			    }	
			    if (command.contains(" ") && !command.substring(command.indexOf(" ") + 1).equals("")) { // if command has 2 parts, the command itself and file
			    	String fileName = command.substring(command.indexOf(" ") + 1); // fileName
					if (command.substring(0, command.indexOf(" ")).equals("get")) { // get command
					    handleGet(fileName, dataOutputStream, out);
					} else if (command.substring(0, command.indexOf(" ")).equals("put")) { // put command
					    handlePut(dataInputStream);								
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
					handleQuit();
					break;		
			    } else {
			    	out.println("Unknown command");
			    }
			} // while	
		    } catch (IOException e) {
		    	System.err.println("IOException in client handler");
		    } 
		} // run
		
		/********** Commands ***********/
		
		// potential method to use for get and put methods, see if terminate command has been entered
		private boolean checkStatus() {
			return true;
		}
		
		private void handleGet(String fileName, DataOutputStream dataOutputStream, PrintWriter out) throws IOException {
			File fileToDownload = new File(remoteCurrDir.getAbsolutePath() + "/" + fileName);
		    if (fileToDownload.isFile()) {
			    out.println("successful"); // means the file is valid and server can start sending the file data to the client
								
				// now getting the file's contents and name and sending it over to the client
				FileInputStream fr = new FileInputStream(fileToDownload.getAbsolutePath());
				byte[] fileNameBytes = fileName.getBytes();
				byte[] fileContentBytes = new byte[(int)fileToDownload.length()];
				fr.read(fileContentBytes);
								
				dataOutputStream.writeInt(fileNameBytes.length);
				dataOutputStream.write(fileNameBytes);
								
				dataOutputStream.writeInt(fileContentBytes.length);
				dataOutputStream.write(fileContentBytes);
		    } else {
		    	out.println(fileName + " does not exist");
		    }
		} // handleGet
		
		private void handlePut(DataInputStream dataInputStream) throws IOException{
			// reading the file information that the client sent
		    int fileNameLength = dataInputStream.readInt();
		    byte[] fileNameBytes = new byte[fileNameLength];
		    dataInputStream.readFully(fileNameBytes, 0, fileNameLength);
		    String clientFileName = new String(fileNameBytes);
						
		    int fileContentLength = dataInputStream.readInt();
		    byte[] fileContentBytes = new byte[fileContentLength];
		    dataInputStream.readFully(fileContentBytes, 0, fileContentLength);
	
		    // Creating the file and writing its contents
		    File downloadedFile = new File(remoteCurrDir.getAbsolutePath() + "/" + clientFileName);
		    try {
				FileOutputStream fileOutputStream = new FileOutputStream(downloadedFile);
				fileOutputStream.write(fileContentBytes);
				fileOutputStream.close();
		    } catch (IOException error) {
		    	error.printStackTrace();
		    }		
		} // handlePut
		
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

