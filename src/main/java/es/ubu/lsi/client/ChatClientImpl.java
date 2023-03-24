package es.ubu.lsi.client;
 
 
import java.io.*;
import java.net.*;
import java.nio.CharBuffer;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;
 
public class ChatClientImpl implements ChatClient{
    private String server;
    private String username;
    private int port;
    private boolean carryOn = true;
    private int id;
 
    private Socket socket; 
    
    private ObjectOutputStream outo;
 	private ObjectInputStream ino;

    private BufferedReader stdIn;

 
    public ChatClientImpl(String server, int port, String username){
        this.server = server;
        this.port = port;
        this.username = username;
        this.id = -1;
    }
    
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
 
    public synchronized void sendMessage(ChatMessage msg) {
        try{
            outo.writeObject(msg);
        }catch(IOException e){
            System.err.println("Ha ocurrido un error al mandar el mensaje");
            System.err.println(e.getMessage());
        }
    }
 
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