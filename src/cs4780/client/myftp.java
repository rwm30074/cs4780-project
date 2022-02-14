package cs4780.client;

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

public class myftp {
	static File localCurrDir = new File(System.getProperty("user.dir"));
	
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
			Socket toServer = new Socket(host, nPortNumber);
			Socket terminateSocket = new Socket(host, tPortNumber);
			
			BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
			BufferedReader in = new BufferedReader(new InputStreamReader(toServer.getInputStream()));
			PrintWriter out = new PrintWriter(toServer.getOutputStream(), true);
			
			DataInputStream dataInputStream = new DataInputStream(toServer.getInputStream());
			DataOutputStream dataOutputStream = new DataOutputStream(toServer.getOutputStream());
						
			// read client's inputs
			while(true) {	
				System.out.print("mytftp> ");
				String command = keyboard.readLine();
				
				if (command.equals("quit") || command.equals("end-server")) {
					out.println(command); // notify server of quit
					break;	
				} else if (command.equals("ls") || command.equals("pwd")) { // these commands require printing info
					handleLsOrPwd(command, in, out);
				} else if (command.contains(" ") && command.substring(0, command.indexOf(" ")).equals("put")) {
					handlePut(command, dataOutputStream, out);
				} else if (command.contains(" ") && command.substring(0, command.indexOf(" ")).equals("get")) {
					handleGet(command, dataInputStream, in, out);
				} else if (!command.equals("ls") && !command.equals("pwd")) { 
					printServerResponse(command, in, out);
				} else {
					out.println(command); // send command to server
				}
			}
			
			in.close();
			out.close();
		} catch (IOException e) {
			System.err.println("Unable to establish connection with server due to unknown machine name or invalid port number(s)");
		} catch (IllegalArgumentException e) {
			System.err.println("Invalid port, port number is out of range");
		}
	}
	
	/************ Commands *************/
	
	private static void handleLsOrPwd(String command, BufferedReader in, PrintWriter out) throws IOException {
		out.println(command); // notify server of either ls or pwd
		String serverResponse = in.readLine(); // read server response
		System.out.println(serverResponse);
	} // handleLsOrPwd
	
	private static void handlePut(String command, DataOutputStream dataOutputStream, PrintWriter out) throws IOException {
		String fileName = command.substring(command.indexOf(" ") + 1);
		File sendFile = new File(localCurrDir.getAbsolutePath() + "/" + fileName); // create file object of the file being put on the server
		if (sendFile.isFile()) {
			out.println(command); // notify server of put command
			FileInputStream fileInputStream = new FileInputStream(sendFile.getAbsolutePath());
			byte[] fileNameBytes = fileName.getBytes();
			byte[] fileContentBytes = new byte[(int) sendFile.length()];
			fileInputStream.read(fileContentBytes); // reading contents of the file
			dataOutputStream.writeInt(fileNameBytes.length); // output length of file name to server
			dataOutputStream.write(fileNameBytes); // out file name to server
			dataOutputStream.writeInt(fileContentBytes.length); // output length of file contents to server
			dataOutputStream.write(fileContentBytes); // output file contents to server
		} else {
			System.err.println(fileName + " does not exist");
		}
	} // handlePut
	
	private static void handleGet(String command, DataInputStream dataInputStream, BufferedReader in, PrintWriter out) throws IOException {
		out.println(command); // send get command to server
		String serverResponse = in.readLine(); // read server response
		if (!serverResponse.equalsIgnoreCase("successful")) { // an error exists, in which the specified file does not exist
			System.err.println(serverResponse);
		} else {
			int fileNameLength = dataInputStream.readInt();
			byte[] fileNameBytes = new byte[fileNameLength];
			dataInputStream.readFully(fileNameBytes, 0, fileNameLength);
			String fileName = new String(fileNameBytes);
			
			int fileContentLength = dataInputStream.readInt();
			byte[] fileContentBytes = new byte[fileContentLength];
			dataInputStream.readFully(fileContentBytes, 0, fileContentLength);

			File downloadedFile = new File(fileName); // create file that is "getted"
			
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(downloadedFile);
				
				
				/* WORK IN PROGRESS, feel free to change it
				
				int numberOfLoops = fileContentBytes.length / 1000;
				if (fileContentBytes.length % 1000 != 0) {
					numberOfLoops = fileContentBytes.length / 1000;
				}
				
				for (int i = 0; i < numberOfLoops; i++) {
					fileOutputStream.write(fileContentBytes, 0, 1000); // put contents into the created file
					System.out.println("Check if terminate");
				}
				*/
				
				fileOutputStream.write(fileContentBytes); // put contents into the created file
				fileOutputStream.close();
			} catch (IOException error) {
				error.printStackTrace();
			}
		}
	} // handleGet
	
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

