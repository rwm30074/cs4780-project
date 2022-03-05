package cs4780.server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class CommandCenter {

	private final static int NUMBER_BYTES_READ = 2;
	private final static int SLEEP_AMOUNT = 500;
	
	private CommandIDTable commandIDTable;
	private boolean isPutting = false;
	private boolean isGetting = false;
	
	public CommandCenter(CommandIDTable commandIDTable) {
		this.commandIDTable = commandIDTable;
	}
		
	public synchronized void handleGet(String command, DataOutputStream dataOutputStream, PrintWriter out, File remoteCurrDir) throws IOException {
		isGetting = true;
		
		while(isPutting) {
			try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
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
		
		isGetting = false;
		notify();
	} // handleGet
		
	public synchronized void handlePut(String command, DataInputStream dataInputStream, PrintWriter out, File remoteCurrDir) throws IOException {
		isPutting = true;
		
		while(isGetting) {
	    	try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	    }
		
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
	    
	    if (!command.contains("&")) {
	    	writeFile(command, clientFileName, fileContentBytes, remoteCurrDir, commandID);
	    } else {
	    	new Thread(() -> {
	    		writeFile(command, clientFileName, fileContentBytes, remoteCurrDir, commandID);
	    	}).start();
	    }
	    isPutting = false;
		notify();
	} // handlePut
	
	private synchronized void writeFile(String command, String clientFileName, byte[] fileContentBytes, File remoteCurrDir, int commandID) {
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
					
					// Check for termination
					if (commandIDTable.isTerminated(commandID) == true) {
						fileOutputStream.close();
						downloadedFile.delete();
						isTerminated = true;
						break;
					}
					startReadingAt = startReadingAt + NUMBER_BYTES_READ;
					if (startReadingAt + NUMBER_BYTES_READ > lengthOfFile) {
						readingLength = lengthOfFile - startReadingAt;
						oneMoreWrite = true;
					}
					try {
						Thread.sleep(SLEEP_AMOUNT);
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
	}
	
	public synchronized void handleMkdir(String fileName, PrintWriter out, File remoteCurrDir) {
		while(isGetting || isPutting) {
	    	try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	    }
		
		File newDir = new File(remoteCurrDir.toURI().getPath() + fileName);
	    if (!newDir.exists()){
			newDir.mkdirs();
			out.println("successful");
		} else {
			out.println("Directory already exists");
		}
	} // handleMkdir
	
	public synchronized void handleLs(PrintWriter out, File remoteCurrDir) {
		while(isGetting || isPutting) {
	    	try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	    }
		
		File[] listOfFiles = remoteCurrDir.listFiles();
		String fileNames = "";
		for (int i = 0; i < listOfFiles.length; i++) {
		    fileNames = fileNames + listOfFiles[i].getName() + "  ";
		}
		out.println(fileNames);
	} // handleLs
	
	public synchronized void handleDelete(String fileName, PrintWriter out, File remoteCurrDir) {
		while(isGetting || isPutting) {
	    	try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	    }
		
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
	
	public synchronized void handleQuit(Socket toClient, BufferedReader in, PrintWriter out) throws IOException {
		while(isGetting || isPutting) {
	    	try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	    }
		
		myftpserver.decrementClientCount();
		in.close();
		out.close();
		toClient.close();
	} // handleQuit
	
	public synchronized File handleCd(String fileName, PrintWriter out, File remoteCurrDir) {
		while(isGetting || isPutting) {
	    	try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	    }
		
		File currDir = new File(remoteCurrDir.getAbsolutePath()); // file to traverse directories
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
						if (currDir.getParentFile() == null) {
							out.println("No such parent folder exists");
							return returnFile;
						} else {
							currDir = currDir.getParentFile();
						}
					} else {
					    tempFile = new File(currDir.getAbsolutePath() + "/" + part);
					    currDir = tempFile;
					}
			    }
			}
		    out.println("successful");
		    return currDir;
	    } else {
	    	out.println("No such directory exists");
	    	return returnFile;
	    }
	} // handleCd
	
	
}
