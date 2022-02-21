package cs4780.client;

//client 1

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;


public class myftp {
	public static final int NUMBER_BYTES_READ = 2;
	private static File localCurrDir = new File(System.getProperty("user.dir"));
	private static Socket toServer = null;
	private static HashMap<Integer, Boolean> activeCommandIDs = new HashMap<Integer, Boolean>();
	
	public static void main(String[] args) {
		
		// Reading command-line arguments and make sure they are valid
		String host = "";
		int nPortNumber = -1;
		int tPortNumber = -1;
		if (args.length == 3) {
			try {
				host = args[0];
		        nPortNumber = Integer.parseInt(args[1]);
		        tPortNumber = Integer.parseInt(args[2]);
		        if (nPortNumber == tPortNumber) {
		        	System.err.println("Port numbers cannot be the same");
		        	System.exit(1);
		        }
		    } catch (NumberFormatException e) {
		        System.err.println("Last 2 arguments must be integers");
		        System.exit(1);
		    }
		} else {
			System.err.println("Must specify machine name, normal port number, and terminate port number");
			System.exit(1);
		}
		
		try {
			// Establish connection with server socket
			toServer = new Socket(host, nPortNumber);
			Socket terminateSocket = new Socket(host, tPortNumber);
			
			BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
			BufferedReader in = new BufferedReader(new InputStreamReader(toServer.getInputStream()));
			PrintWriter out = new PrintWriter(toServer.getOutputStream(), true);
			
			DataInputStream dataInputStream = new DataInputStream(toServer.getInputStream());
			DataOutputStream dataOutputStream = new DataOutputStream(toServer.getOutputStream());
			
			BufferedReader inTerminate = new BufferedReader(new InputStreamReader(terminateSocket.getInputStream()));
			PrintWriter outTerminate = new PrintWriter(terminateSocket.getOutputStream(), true);
						
			// read client's inputs
			while(true) {	
				System.out.print("mytftp> ");
				String command = keyboard.readLine();
				if (command.equals("quit") || command.equals("end-server")) {
					outTerminate.println(command);
					out.println(command); // notify server of quit
					break;	
				} else if (command.equals("ls") || command.equals("pwd")) { // these commands require printing info
					handleLsOrPwd(command, in, out);
				} else if (command.contains(" ") && command.substring(0, command.indexOf(" ")).equals("put")) {
					handlePut(command, in, dataOutputStream, out);
				} else if (command.contains(" ") && command.substring(0, command.indexOf(" ")).equals("get")) {
					handleGet(command, dataInputStream, in, out);
				} else if (command.contains(" ") && command.substring(0, command.indexOf(" ")).equals("terminate")) {
					handleTerminate(command, inTerminate, outTerminate);
				} else if (!command.equals("ls") && !command.equals("pwd")) { 
					printServerResponse(command, in, out);
				} else {
					out.println(command); // send command to server
				}
			}
			
			in.close();
			out.close();
			inTerminate.close();
			outTerminate.close();
			terminateSocket.close();
		} catch (IOException e) {
			System.err.println("Unable to establish connection with server due to unknown machine name or invalid port number(s)");
		} catch (IllegalArgumentException e) {
			System.err.println("Invalid port, port number is out of range");
		}
	}
	
	/************ Commands *************/
	
	private static void handleTerminate(String command, BufferedReader inTerminate, PrintWriter outTerminate) throws IOException {
		// if terminatecommandID array still has the commandID, that means it was not terminated
		if (command.contains(" ") && !command.substring(command.indexOf(" ") + 1).equals("")) {
			outTerminate.println(command); // send over command to server
			int commandID = Integer.parseInt(command.substring(command.indexOf(" ") + 1));
			outTerminate.println(commandID);
			if (activeCommandIDs.containsKey(commandID)) {
				// get command
				//handle here on client
				if (activeCommandIDs.get(commandID) != false) {
					outTerminate.println("update-table");
					activeCommandIDs.put(commandID, false);
				} else {
					outTerminate.println("nothing");
					System.err.println("Cannot terminate command " + commandID + ". The command has already ended");
				}
			} else {
				// put command
				// handle on server side
				outTerminate.println("to-server");	
				System.out.println(inTerminate.readLine());
			}

		} else {
			System.err.println("Incorrect syntax to terminate");
		}
	}
	
	private static void handleLsOrPwd(String command, BufferedReader in, PrintWriter out) throws IOException {
		out.println(command); // notify server of either ls or pwd
		String serverResponse = in.readLine(); // read server response
		System.out.println(serverResponse);
	} // handleLsOrPwd
	
	
	 private static void handlePut(String command, BufferedReader in, DataOutputStream dataOutputStream, PrintWriter out) throws IOException {
		String fileName = "";
		boolean willSendFile = true;
		if (!command.contains("&")) {
			fileName = command.substring(command.indexOf(" ") + 1);
		} else if (command.contains("&") && command.charAt(command.indexOf("&") - 1) == ' '){
			fileName = command.substring(command.indexOf(" ") + 1, command.indexOf("&") - 1);
		} else {
			System.err.println("Incorrect syntax for put");
			willSendFile = false;
		}
		if (willSendFile) {
			//System.out.println("Thread on client: " + Thread.currentThread().getName());
			File sendFile = new File(localCurrDir.getAbsolutePath() + "/" + fileName); // create file object of the file being put on the server
			if (sendFile.isFile()) {
				out.println(command); // notify server of put command
				String commandID = in.readLine();
				System.out.println("Command ID: " + commandID);
				FileInputStream fileInputStream = new FileInputStream(sendFile.getAbsolutePath());
				byte[] fileNameBytes = fileName.getBytes();
				byte[] fileContentBytes = new byte[(int) sendFile.length()];
				fileInputStream.read(fileContentBytes); // reading contents of the file
				dataOutputStream.writeInt(fileNameBytes.length); // output length of file name to server
				dataOutputStream.write(fileNameBytes); // out file name to server
				dataOutputStream.writeInt(fileContentBytes.length); // output length of file contents to server
				dataOutputStream.write(fileContentBytes); // output file contents to server
				fileInputStream.close();
			} else {
				System.err.println(fileName + " does not exist");
			}
		}
	}
	
	private static void handleGet(String command, DataInputStream dataInputStream, BufferedReader in, PrintWriter out) throws IOException {
		out.println(command); // send get command to server
		String serverResponse = in.readLine(); // read server response
		if (!serverResponse.equalsIgnoreCase("successful")) { // an error exists, in which the specified file does not exist
			System.err.println(serverResponse);
		} else {
			int commandID = Integer.parseInt(in.readLine());
			System.out.println("Command ID: " + commandID);
			activeCommandIDs.put(commandID, true);
	
			
			int fileNameLength = dataInputStream.readInt();
			byte[] fileNameBytes = new byte[fileNameLength];
			dataInputStream.readFully(fileNameBytes, 0, fileNameLength);
			String fileName = new String(fileNameBytes);
			
			int fileContentLength = dataInputStream.readInt();
			byte[] fileContentBytes = new byte[fileContentLength];
			dataInputStream.readFully(fileContentBytes, 0, fileContentLength);
			
			if (!command.contains("&")) {
		    	writeFile(fileName, fileContentBytes, commandID);
		    } else {
		    	new Thread(() -> {
		    		writeFile(fileName, fileContentBytes, commandID);
		    	}).start();
		    }
		}
	} // handleGet
	
	synchronized private static void writeFile(String fileName, byte[] fileContentBytes, int commandID) {
	    // Creating the file and writing its contents
	    File downloadedFile = new File(fileName);
	    try {
			FileOutputStream fileOutputStream = new FileOutputStream(downloadedFile);
			boolean oneMoreWrite = false;
			int lengthOfFile = fileContentBytes.length;
			int startReadingAt = 0;
			int readingLength = NUMBER_BYTES_READ;
			int i = 1;
			if (lengthOfFile < NUMBER_BYTES_READ) {
				fileOutputStream.write(fileContentBytes, startReadingAt, lengthOfFile);
			} else {
				//System.out.println("Number of bytes: " + lengthOfFile);
				boolean isTerminated = false;
				while(!oneMoreWrite) {
					//System.out.println(i);
					//i++;
					fileOutputStream.write(fileContentBytes, startReadingAt, readingLength);
					// CHECK FOR TERMINATION HERE
					
					if (activeCommandIDs.get(commandID) == false) {
						System.out.println("Terminating file!!!!");
						fileOutputStream.close();
						if (downloadedFile.delete()) {
							System.out.println("successful delete");
						} else {
							System.out.println("Failed to delete)");
						}
						isTerminated = true;
						//commandIDContainer.remove(commandID); // termination has ended
						break;
					}
					
					startReadingAt = startReadingAt + NUMBER_BYTES_READ;
					if (startReadingAt + NUMBER_BYTES_READ > lengthOfFile) {
						readingLength = lengthOfFile - startReadingAt;
						oneMoreWrite = true;
					}
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} // while
				if (isTerminated == false) {
					fileOutputStream.write(fileContentBytes, startReadingAt, readingLength);
					//commandIDContainer.put(commandID, true);
				}
				
				activeCommandIDs.put(commandID, false); // command has finished, so it becomes inactive
			}
			fileOutputStream.close();
	    } catch (IOException error) {
	    	error.printStackTrace();
	    }
	}
	
	private static void printServerResponse(String command, BufferedReader in, PrintWriter out) throws IOException {
		// if the command is not "ls" or "pwd", check to see if the command is successful. If not, print
		// it's error. Otherwise, nothing should be printed
		// The ls and pwd commands are technically always successful since they simply print what is within a directory,
		// which is why they are not included in this check
		out.println(command); // send command to server
		String serverResponse = in.readLine(); // read server response
		if (!serverResponse.equalsIgnoreCase("successful")) { // an error exists
			System.err.println(serverResponse);
		}
	} // printServerResponse
	
}