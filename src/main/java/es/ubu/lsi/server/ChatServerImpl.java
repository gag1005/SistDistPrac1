package es.ubu.lsi.server;

import java.net.*;
import java.io.*;
import java.text.*;
import java.util.HashMap;
import java.util.Map;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;

public class ChatServerImpl implements ChatServer {

	private static final int DEFAULT_PORT = 1500;
	private final int clientId;
	private final SimpleDateFormat sdf;
	private int port;
	private boolean alive;
	private ServerSocket serverSocket;
	private int currentId;

	private Map<Integer, String> userNames;
	private Map<Integer, Socket> userSockets;
	private Map<Integer, ServerThreadForClient> userThreads;
	private Map<Integer, Boolean> isRunning;

	public static void main(String[] args) throws IOException {
		ChatServer chatServer;
		if (args.length >= 1) {
			chatServer = new ChatServerImpl(Integer.parseInt(args[0]));
		} else {
			chatServer = new ChatServerImpl(DEFAULT_PORT);
		}

		chatServer.startup();
	}

	public ChatServerImpl(int port) {
		this.alive = true;
		this.port = port;
		this.clientId = 0;
		this.sdf = new SimpleDateFormat();
		this.currentId = 1;
		this.userNames = new HashMap<Integer, String>();
		this.userSockets = new HashMap<Integer, Socket>();
		this.userThreads = new HashMap<Integer, ServerThreadForClient>();
		this.isRunning = new HashMap<Integer, Boolean>();
	}

	public void startup() {
		try {
			serverSocket = new ServerSocket(this.port);
		} catch (IOException e) {
			System.out.println("Cagaste");
		}
		try {
			PrintWriter out = null;
			BufferedReader in = null;
			Socket clientSocket;
			while (alive) {
				try {
					clientSocket = serverSocket.accept();					
				} catch (Exception e) {
					System.out.println("Se ha cerrado el servidor");
					return;
				}
				System.out.println("Nuevo Cliente: " + clientSocket.getInetAddress() + "/" + clientSocket.getPort());
				out = new PrintWriter(clientSocket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

				// System.out.println(in.readLine());
				out.println(String.valueOf(currentId));
				System.out.println("Asignada id: " + currentId);
				String nombre = in.readLine();
				userNames.put(currentId, nombre);
				userSockets.put(currentId, clientSocket);

				ServerThreadForClient hilonuevocliente = new ServerThreadForClient(clientSocket, currentId, nombre);
				userThreads.put(currentId, hilonuevocliente);
				hilonuevocliente.start();
				isRunning.put(currentId, Boolean.TRUE);
				currentId++;
			}
			in.close();
			out.close();
		} catch (IOException e) {
			System.out.println(
					"Exception caught when trying to listen on port " + this.port + " or listening for a connection");
			System.out.println(e.getMessage());
		}
	}

	public synchronized void shutdown() {
		alive = false;
		Object keys[] = userSockets.keySet().toArray();
		for (Object id : keys) {
			remove((Integer) id);
		}
		try {			
			serverSocket.close();
		}catch (IOException e) {
			System.err.println("Se ha producido un error al cerrar el servidor");
		}
	}

	public synchronized void broadcast(ChatMessage message) {
		for (Integer id : userSockets.keySet()) {
			if (id != message.getId() || message.getId() == 0) {
				String text = "";
				// No se pone el nombre en caso de que sea un mensaje del servidor
				if (message.getId() != 0) {
					text += "<" + userNames.get(message.getId()) + ">: ";
				}
				text += message.getMessage();
				try {
					PrintWriter out = new PrintWriter(userSockets.get(id).getOutputStream(), true);
					out.println(text);

				} catch (IOException e) {
					System.err.println("Cagaste");
				}
			}
		}
	}

	public synchronized void remove(int id) {
		try {
			isRunning.put(id, Boolean.FALSE);
			System.out.println("Eliminando a " + id);
			userThreads.get(id).closeSocket();
			userThreads.get(id).interrupt();
			userThreads.remove(id);
			userSockets.remove(id);
			userNames.remove(id);
			isRunning.remove(id);
		} catch (Exception e) {
			System.err.println("No se ha cerrado");
		}
	}

	class ServerThreadForClient extends Thread {

		private Socket clientSocket;
		private int id;
		private String username;
		private ObjectInputStream in;

		private ServerThreadForClient(Socket clientSocket, int id, String username) {
			this.clientSocket = clientSocket;
			this.id = id;
			this.username = username;
			try {
				this.in = new ObjectInputStream(clientSocket.getInputStream());
			} catch (IOException e) {
				System.err.println("Cagaste");
			}
		}

		public void closeSocket() {
			try {
				in.close();
			} catch (IOException e) {
				System.err.println("Cagaste");
			}
		}

		public void run() {
            try {         
            	 ChatMessage newUserMessage = new ChatMessage(0, MessageType.MESSAGE, userNames.get(id) + " se ha unido al chat.");
            	 broadcast(newUserMessage);
		         ChatMessage cm;
		         while((cm = (ChatMessage) in.readObject()) != null && isRunning.get(id) == Boolean.TRUE)  {
		        	if (isRunning.get(id) == Boolean.FALSE) {
		        		clientSocket.close();
		        		return;
		        	}
		        	System.out.println(cm.getMessage());
		        	System.out.println("Mensaje recibido de " + id + " (" + userNames.get(id) + ") " + cm.getType());
	                switch (cm.getType()) {
	                	case LOGOUT:
	                		remove(Integer.parseInt(cm.getMessage()));
	                        break;
	                    case MESSAGE:
	                        broadcast(cm);
	                        break;
	                    case SHUTDOWN:
	                        shutdown();
	                        break;
	            	}
		         }
		         in.close();
				 clientSocket.close();
            }catch(IOException e){
                return;
            }catch(ClassNotFoundException e){
                System.out.println("Cagaste");
            }
        }
	}
}