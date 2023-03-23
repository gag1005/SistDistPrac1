package es.ubu.lsi.server;

import java.net.*;
import java.io.*;
import java.text.*;
import java.util.HashMap;
import java.util.Map;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;

public class ChatServerImpl implements ChatServer {

	/** El puerto pr defecto que usará el servidor. */
	private static final int DEFAULT_PORT = 1500;
	/**  */
	private final int clientId;
	/**  */
	private final SimpleDateFormat sdf;
	/** El puerto que usará el servidor. */
	private int port;
	/** Indica si el servidor sigue aceptando clientes. */
	private boolean alive;
	/** EL socket del servidor .*/
	private ServerSocket serverSocket;
	/** El id que tendrá el proximo cliente, se incrementa clada vez que se conecta un usuario. */
	private int currentId;

	/** Un mapa que contiene los hilos de los clientes, usa las IDs como clave. */
	private Map<Integer, ServerThreadForClient> userThreads;
	
	/**
	 * Clase principal del servidor, instancia un ChatServer
	 * usando el puerto que se le de por parámetro o el
	 * puerto por defecto en caso de que no se especifique
	 * uno.
	 * 
	 * @param args Los argumentos del programa
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		ChatServer chatServer;
		if (args.length >= 1) {
			chatServer = new ChatServerImpl(Integer.parseInt(args[0]));
		} else {
			chatServer = new ChatServerImpl(DEFAULT_PORT);
		}

		chatServer.startup();
	}

	/**
	 * Constructor de la clase ChatServerImpl.
	 * 
	 * @param port El puerto que se quiere use el servidor.
	 */
	public ChatServerImpl(int port) {
		this.alive = true;
		this.port = port;
		this.clientId = 0;
		this.sdf = new SimpleDateFormat();
		this.currentId = 1;
		this.userThreads = new HashMap<Integer, ServerThreadForClient>();
	}

	/**
	 * Este método contiene el bucle que acepta los clientes
	 * les asigna un ID, recibe el nombre del cliente y
	 * finalmente crea un hilo para ese cliente y lo registra
	 * en el mapa con su id.
	 */
	public void startup() {
		try {
			serverSocket = new ServerSocket(this.port);
		} catch (IOException e) {
			System.err.println("Ha ocurrido un error al crear el socket del servidor");
			System.err.println(e.getMessage());
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
			System.err.println(
					"Exception caught when trying to listen on port " + this.port + " or listening for a connection");
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Se para el bucle que acepta clientes, se desconectan
	 * todos los clientes, se borran sus hilos y finalmente
	 * se cierra el socket del servidor.
	 */
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

	/**
	 * Recibe un mensage y se o manda a todos los clientes
	 * Incluye el nombre del usuario que madó el mensaje
	 * ecxcepto cuando se une un nuevo usuario al chat que
	 * al ser el servidor el que manda el mensaje no se
	 * muestra el nombre.
	 * 
	 * @param message El mensaje a enviar a todos los usuarios del chat.
	 */
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
					System.err.println("Ha habido un error al retransmitir el mensaje");
					System.err.println(e.getMessage());
				}
			}
		}
	}

	/**
	 * Elimina a un usuario del chat dado su ID,
	 * para su hilo y lo borra.
	 * 
	 * @param id El id del usuario que se quiere eliminar
	 */
	public synchronized void remove(int id) {
		ServerThreadForClient userThread = userThreads.get(id);
		userThread.stopExecution();			
		userThread.closeSocket();
		userThread.interrupt();
		userThreads.remove(id);
		System.out.println("Eliminando a " + id);
	}

	class ServerThreadForClient extends Thread {

		//** El socket del cliente. */
		private Socket clientSocket;
		/** El id del cliente. */
		private int id;
		/** El nombre del usuario. */
		private String username;
		/** El input de mensajes. */
		private ObjectInputStream in;
		/** Indica que si el hilo está recibiendo los mensajes del cliente. */
		private boolean isRunning;

		/**
		 * Constructor de la clase ServerThreadForClient.
		 * 
		 * @param clientSocket El socket del cliente asociado a este hilo.
		 * @param id El id del cliente.
		 * @param username El nombre del usuario.
		 */
		private ServerThreadForClient(Socket clientSocket, int id, String username) {
			this.clientSocket = clientSocket;
			this.id = id;
			this.username = username;
			this.isRunning = true;
			try {
				this.in = new ObjectInputStream(clientSocket.getInputStream());
			} catch (IOException e) {
				System.err.println("Ha ocurido un eror durante la creación del ObjectInputStream");
				System.err.println(e.getMessage());
			}
		}
		
		/**
		 * Para la ejecución del bucle principal del hilo.
		 */
		public void stopExecution() {
			this.isRunning = false;
		}
		
		/**
		 * Cierra el socket del cliente.
		 */
		public void closeSocket() {
			try {
				in.close();
			} catch (IOException e) {
				System.err.println("Cagaste");
			}
		}

		/**
		 * Devuelve el socket del cliente asociado
		 * a este hilo.
		 * 
		 * @return El socket del cliente asociado a este hilo.
		 */
		public Socket getClientSocket(){
			return clientSocket;
		}
		
		/**
		 * Devuelve el nombre del usuario asociado
		 * a este hilo.
		 * 
		 * @return EL nombre del usuario asociado a este hilo.
		 */
		public String getUsername() {
			return username;
		}
		
		/**
		 * Contiene el bucle principal del hilo,
		 * escucha los mensajes que envia el
		 * cliente y hace la acción adecuada
		 * según el tipo de mensaje que sea.
		 */
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
                System.err.println("Ha ocurrido un error durante la ejecución del hilo del cliente");
                System.err.println(e.getMessage());
            }
        }
	}
}