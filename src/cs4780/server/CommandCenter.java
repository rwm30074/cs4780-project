package cs4780.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class CommandCenter {

	private final static int NUMBER_BYTES_READ = 2;
	
	private CommandIDTable commandIDTable;
	
	public CommandCenter(CommandIDTable commandIDTable) {
		this.commandIDTable = commandIDTable;
	}
		
	public synchronized void handleGet(String command, DataOutputStream dataOutputStream, PrintWriter out, File remoteCurrDir) throws IOException {
		
		// Get appropriate command id
		boolean willSendFile = true;
		String fileName = "";
		if (!command.contains("&")) {
			fileName = command.substring(command.indexOf(" ") + 1);
		} else if (command.contains("&") && command.charAt(command.indexOf("&") - 1) == ' '){
			fileName = command.substring(command.indexOf(" ") + 1, command.indexOf("&") - 1);
		} else {
			out.println("Incorrect syntax for get");
			willSendFile = false;
		}
		
		if (willSendFile) {
			File fileToDownload = new File(remoteCurrDir.getAbsolutePath() + "/" + fileName);
		    if (fileToDownload.isFile()) {
			    out.println("successful"); // means the file is valid and server can start sending the file data to the client
			    
			    int commandID = commandIDTable.getCurrentCommandID(); // Retrieve commandID
				out.println(commandID);
				commandIDTable.incrementCurrentCommandID();
				
				//String threadName = in.readLine();
				if (command.contains("&")) {
					commandIDTable.addToTable(commandID, false); // Add commandID to table if it does not belong to the main thread
																 // AKA if the user does not use & with the put command
				}
			    
				// now getting the file's contents and name and sending it over to the client
				FileInputStream fr = new FileInputStream(fileToDownload.getAbsolutePath());
				byte[] fileNameBytes = fileName.getBytes();
				byte[] fileContentBytes = new byte[(int)fileToDownload.length()];
				fr.read(fileContentBytes);
								
				dataOutputStream.writeInt(fileNameBytes.length);
				dataOutputStream.write(fileNameBytes);
								
				dataOutputStream.writeInt(fileContentBytes.length);
				dataOutputStream.write(fileContentBytes);
				fr.close();
		    } else {
		    	out.println(fileName + " does not exist");
		    }
		}
	} // handleGet
		
	public void handlePut(String command, DataInputStream dataInputStream, PrintWriter out, File remoteCurrDir) throws IOException {
		int commandID = commandIDTable.getCurrentCommandID(); // Retrieve commandID
		out.println(commandID);
		//print commandID
		commandIDTable.incrementCurrentCommandID(); // Increment commandIDSetter regardless...
		
		// reading the file information that the client sent
	    int fileNameLength = dataInputStream.readInt();
	    byte[] fileNameBytes = new byte[fileNameLength];
	    dataInputStream.readFully(fileNameBytes, 0, fileNameLength);
	    String clientFileName = new String(fileNameBytes);
	    int fileContentLength = dataInputStream.readInt();
	    byte[] fileContentBytes = new byte[fileContentLength];
	    dataInputStream.readFully(fileContentBytes, 0, fileContentLength);
	    
	    //System.out.println("Content length: " + fileContentLength);
	    
	    if (!command.contains("&")) {
	    	writeFile(command, clientFileName, fileContentBytes, remoteCurrDir, commandID);
	    } else {
	    	new Thread(() -> {
	    		writeFile(command, clientFileName, fileContentBytes, remoteCurrDir, commandID);
	    	}).start();
	    }
	} // handlePut
	
	private synchronized void writeFile(String command, String clientFileName, byte[] fileContentBytes, File remoteCurrDir, int commandID) {
		//System.out.println("Thread: " + Thread.currentThread().getName());
		if (command.contains("&")) {
			commandIDTable.addToTable(commandID, false); // Add commandID to table if it does not belong to the normal thread
		}
		
	    // Creating the file and writing its contents
	    File downloadedFile = new File(remoteCurrDir.getAbsolutePath() + "/" + clientFileName);
	    
	    try {
			FileOutputStream fileOutputStream = new FileOutputStream(downloadedFile, false);
			boolean oneMoreWrite = false;
			int lengthOfFile = fileContentBytes.length;
			int startReadingAt = 0;
			int readingLength = NUMBER_BYTES_READ;
			int i = 1;
			if (lengthOfFile < NUMBER_BYTES_READ) {
				fileOutputStream.write(fileContentBytes, startReadingAt, lengthOfFile);
			} else {
				boolean isTerminated = false;
				while(!oneMoreWrite) {
					fileOutputStream.write(fileContentBytes, startReadingAt, readingLength);
					
					if (commandIDTable.isTerminated(commandID) == true) {
						fileOutputStream.close();
						if (downloadedFile.delete()) {
							System.out.println("successful delete");
						} else {
							System.out.println("Failed to delete)");
						}
						isTerminated = true;
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
				}
				commandIDTable.terminate(commandID); // command has finished, mark as terminated so that it cannot be terminated
			}
			fileOutputStream.close();
			
			
	    } catch (IOException error) {
	    	error.printStackTrace();
	    }
	    //notify();
	}
}
