package es.ubu.lsi.client;
 
 
import java.io.*;
import java.net.*;
import java.nio.CharBuffer;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;
 
public class ChatClientImpl implements ChatClient{
	/** Contiene la informacion del servidor*/
    private String server;
    /** Nombre del usuario del cliente*/
    private String username;
    /** Puerto en el que se encuentra el servidor*/
    private int port;
    /** Indica si el cliente sigue activo*/
    private boolean carryOn = true;
    /** Id asignada por el servidor al cliente*/
    private int id;
    /** Socket del cliente*/
    private Socket socket; 
    /** Salida de Mensajes al servidor*/
    private ObjectOutputStream outo;
    /** Entrada de Mensajes del servidor*/
 	private ObjectInputStream ino;
 	/** Entrada de mensajes por consola */
    private BufferedReader stdIn;

	/**
	 * Constructor de la clase ChatClientImpl.
	 * 
	 * @param port El puerto que se quiere use el servidor.
	 * @param server IP donde se encuentra el servidor.
	 * @param username Nombre del cliente que se conecta.
	 */
    public ChatClientImpl(String server, int port, String username){
        this.server = server;
        this.port = port;
        this.username = username;
        this.id = -1;
    }
    
	/**
	 * Este método inicializa las entradas del cliente con el servidor
	 * crea un hilo donde se reciben los mensajes
	 * y finalmente se quedaria atendiendo a la entrada por teclado del cliente
	 */
    public boolean start() {
        try{
            stdIn = new BufferedReader(new InputStreamReader(System.in));
            socket = new Socket(server, port);
            outo = new ObjectOutputStream(socket.getOutputStream());
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + server);
            return false;
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " +
                server);
            return false;
        } 

        ChatClientListener ccl = new ChatClientListener();
        ccl.start();
        createMessage();

        return true;
    }
 /** 
  * Metodo que realiza la salida del Mensaje
  */
    public synchronized void sendMessage(ChatMessage msg) {
        try{
            outo.writeObject(msg);
        }catch(IOException e){
            System.err.println("Ha ocurrido un error al mandar el mensaje");
            System.err.println(e.getMessage());
        }
    }
 /** 
  * Metodo que desconecta a un usuario eliminando las entradas y salidas 
  * del mismo.
  */
    public void disconnet() {
        carryOn = false;
    	try {
    		ino.close();
    		outo.close();
    		socket.close();			
		} catch (Exception e) {
            System.err.println("Ha ocurrido un error al desconectarse");
            System.err.println(e.getMessage());
		}
    }
    /**
     * Metodo que realiza un bucle en espera una entrada de teclado,
     * cuando se recibe se comprueba la primera palabra para saber 
     * que tipo de mensaje es. 
     * Si es SHUTDOWN apaga el servidor
     * Si es LOGOUT desconecta al propio cliente
     * Si es DROP desconecta a otro cliente en caso de que exista
     * En otro caso, es un mensaje.
     */
    private void createMessage(){
        try{
            String inputLine;
            System.out.print("> ");
            while (carryOn && (inputLine = stdIn.readLine()) != null) {
                if (carryOn == false){
                    disconnet();
                    System.out.println("Te han desconectado");
                    break;
                }
                
            	if(outo != null) {	
            		outo.reset();
            	}
            	
                ChatMessage cm = null;
                String type = inputLine.split(" ")[0].toUpperCase();
                if(type.equals("SHUTDOWN")){
                    cm = new ChatMessage(id, MessageType.SHUTDOWN, "");
                    sendMessage(cm);
                }else if(type.equals("LOGOUT")){
                    cm = new ChatMessage(id, MessageType.LOGOUT, username);
                    sendMessage(cm);
                    carryOn = false;
                }else if(type.equals("DROP")){
                    cm = new ChatMessage(id, MessageType.LOGOUT, inputLine.split(" ")[1]);
                    sendMessage(cm);
                }else{
                    cm = new ChatMessage(id, MessageType.MESSAGE, inputLine);
                    sendMessage(cm);
                }
                System.out.print("> ");
            }
        }catch(IOException e){
        	System.err.println("Ha ocurrido un error en la creación del mensaje");
            System.err.println(e.getMessage());
        }
    }
 /** Metodo principal donde los argumentos son
  * 
  * args[0] es el host
  * args[1] es el puerto 
  * args[2] es el nombre del cliente
  */
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println(
                "Usage: java EchoClient <username> <port> <host>");
            System.exit(1);
        }
        ChatClient chatClient = new ChatClientImpl(args[2], Integer.parseInt(args[1]), args[0]);
        chatClient.start();
    }
 
    

    private class ChatClientListener extends Thread{

    	/**
    	 * Metodo que contiene el bucle del hilo
    	 * Al iniciarlo, genera los mensajes que es enviaran al servidor
    	 * donde recibe la id que le ha asignado el servidor
    	 * y le manda el nombre del cliente.
    	 * Posteriormente, se queda a la espera de que el servidor mande
    	 * los mensajes informativos al cliente
    	 */
        public void run(){
            ChatMessage mensaje;
            try{
                ino = new ObjectInputStream(socket.getInputStream());

                ChatMessage message = (ChatMessage) ino.readObject();
                id = Integer.parseInt(message.getMessage());
                ChatMessage nameMessage = new ChatMessage(id, MessageType.MESSAGE, username);
                // outo.writeObject(nameMessage); 
                sendMessage(nameMessage);
                System.out.println("Conectado a " + server + " con id: " + id);

                while (carryOn && (mensaje = (ChatMessage) ino.readObject()) != null){
                	if (mensaje.getType() == MessageType.LOGOUT) {
                		// disconnet();
                        carryOn = false;
                	}else {
                		System.out.print("\b\b");
                		System.out.println(mensaje.getMessage());
                		System.out.print("> ");
                	}
                }
                ino.close();
            }catch(IOException e){
                System.err.println("Ha ocurrido un error el thread del cliente");
                System.err.println(e.getMessage());
            }catch(ClassNotFoundException e) {
            	System.err.println("Ha ocurrido un error el thread del cliente");
                System.err.println(e.getMessage());
            }
        }
    }
}