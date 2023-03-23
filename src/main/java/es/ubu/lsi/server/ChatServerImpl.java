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

	private Map<Integer, ServerThreadForClient> userThreads;

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
		this.userThreads = new HashMap<Integer, ServerThreadForClient>();
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

				ServerThreadForClient hilonuevocliente = new ServerThreadForClient(clientSocket, currentId, nombre);
				userThreads.put(currentId, hilonuevocliente);
				hilonuevocliente.start();
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
		Object keys[] = userThreads.keySet().toArray(); 
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
		for (Integer id : userThreads.keySet()) {
			if (id != message.getId() || message.getId() == 0) {
				String text = "";
				// No se pone el nombre en caso de que sea un mensaje del servidor
				if (message.getId() != 0) {
					text += "<" + userThreads.get(message.getId()).getUsername() + ">: ";
				}
				text += message.getMessage();
				try {
					PrintWriter out = new PrintWriter(userThreads.get(id).getClientSocket().getOutputStream(), true);
					out.println(text);

				} catch (IOException e) {
					System.err.println("Cagaste");
				}
			}
		}
	}

	public synchronized void remove(int id) {
		try {
			ServerThreadForClient userThread = userThreads.get(id);
			userThread.stopExecution();
			
			System.out.println("Eliminando a " + id);
			userThreads.get(id).closeSocket();
			userThreads.get(id).interrupt();
			userThreads.remove(id);
		} catch (Exception e) {
			System.err.println("No se ha cerrado");
		}
	}

	class ServerThreadForClient extends Thread {

		private Socket clientSocket;
		private int id;
		private String username;
		private ObjectInputStream in;
		private boolean isRunning;

		private ServerThreadForClient(Socket clientSocket, int id, String username) {
			this.clientSocket = clientSocket;
			this.id = id;
			this.username = username;
			this.isRunning = true;
			try {
				this.in = new ObjectInputStream(clientSocket.getInputStream());
			} catch (IOException e) {
				System.err.println("Cagaste");
			}
		}
		
		public void stopExecution() {
			this.isRunning = false;
		}
		
		public void closeSocket() {
			try {
				in.close();
			} catch (IOException e) {
				System.err.println("Cagaste");
			}
		}

		public Socket getClientSocket(){
			return clientSocket;
		}
		
		public void setUserame(String name) {
			this.username = name;
		}
		
		public String getUsername() {
			return username;
		}
		
		public void run() {
            try {         
            	 ChatMessage newUserMessage = new ChatMessage(0, MessageType.MESSAGE, username + " se ha unido al chat.");
            	 broadcast(newUserMessage);
		         ChatMessage cm;
		         while((cm = (ChatMessage) in.readObject()) != null && isRunning)  {
		        	if (!isRunning) {
		        		clientSocket.close();
		        		return;
		        	}
		        	System.out.println(cm.getMessage());
		        	System.out.println("Mensaje recibido de " + id + " (" + username + ") " + cm.getType());
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