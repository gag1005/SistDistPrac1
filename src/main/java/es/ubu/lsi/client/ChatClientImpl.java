package es.ubu.lsi.client;
 
 
import java.io.*;
import java.net.*;
 
import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;
 
public class ChatClientImpl implements ChatClient{
    private String server;
    private String username;
    private int port;
    private boolean carryOn = true;
    private int id;
    private ObjectOutputStream oout;
 
    private Socket socket; 
 
    public ChatClientImpl(String server, int port, String username){
        this.server = server;
        this.port = port;
        this.username = username;
        this.id = -1;
    }
 
    public boolean start() {
     	PrintWriter out;
    	BufferedReader in;
        try{
            socket = new Socket(server, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + server);
            return false;
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " +
                server);
            return false;
        } 

        try{
        	id = Integer.parseInt(in.readLine());
    		out.println(username); 
            System.out.println("Conectado a " + server + " con id: " + id);
            ChatClientListener ccl = new ChatClientListener();
            ccl.start();
        }catch(Exception e){
            System.err.println("Ehehehe");
        }
        createMessage();

        return true;
    }
 
    public void sendMessage(ChatMessage msg) {
        try{
            oout.writeObject(msg);
        }catch(IOException e){
            System.err.println("No voy a poner cagaste");
        }
    }
 
    public void disconnet() {
    	System.out.println("Te has desconectado");
        carryOn = false;
    }
    
    private void createMessage(){
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        try{
            String inputLine;
            oout = new ObjectOutputStream(socket.getOutputStream());
            while (carryOn && (inputLine = stdIn.readLine()) != null) {
            	if(oout != null) {	
            		oout.reset();
            	}
            	
                ChatMessage cm = null;
                String type = inputLine.split(" ")[0].toUpperCase();
                if(type.equals("SHUTDOWN")){
                    cm = new ChatMessage(id, MessageType.SHUTDOWN, "");
                    sendMessage(cm);
                }else if(type.equals("LOGOUT")){
                	
                    cm = new ChatMessage(id, MessageType.LOGOUT, Integer.toString(id));
                    sendMessage(cm);
                    disconnet();
                    
                    //System.out.println(Integer.toString(id));
                }else if(type.equals("DROP")){
                    cm = new ChatMessage(id, MessageType.LOGOUT, inputLine.split(" ")[1]);
                    sendMessage(cm);
                }else{
                    cm = new ChatMessage(id, MessageType.MESSAGE, inputLine);
                    sendMessage(cm);
                }
            }
        }catch(IOException e){
        	System.out.println("Te han desconectado");
            System.exit(0);        
        }
    }
 
    public static void main(String[] args) {
        /*if (args.length != 2) {
            System.err.println(
                "Usage: java EchoClient <host name> <username>");
            System.exit(1);
        }*/
        ChatClient chatClient = new ChatClientImpl("localhost", 1500, "Test");
        chatClient.start();
    }
 

    private class ChatClientListener extends Thread{

        public void run(){
            BufferedReader in;
            String mensaje;
            try{
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while (carryOn&&(mensaje = in.readLine()) != null){
                    System.out.println(mensaje);
                }
                in.close();
            }catch(IOException e){
                System.err.println("ODIO LOS TRY CATCH");
            }
        }
    }
}