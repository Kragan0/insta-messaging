package utb.fai;

import java.util.*;

import com.sun.nio.sctp.HandlerResult;


public class ActiveHandlers {
    private static final long serialVersionUID = 1L;
    private HashSet<SocketHandler> activeHandlersSet = new HashSet<SocketHandler>();


    synchronized SocketHandler getHandler(String username)
    {
        SocketHandler socketHandler = null; 
        for (SocketHandler handler : activeHandlersSet) {
            if (handler.username.equals(username)){
                socketHandler = handler;
                }
        } 
        return socketHandler;
    }
   
 
    synchronized boolean usernameExists(String username) {
        for (SocketHandler handler : activeHandlersSet) {
            if (handler.username.equals(username))
            {
               return true; 
            }
        }
        return false;
    }

    synchronized void sendPrivateMessage(String receiverName, String message)
    {
        SocketHandler receiver = getHandler(receiverName); 
        for (SocketHandler handler : activeHandlersSet) {
            if (handler == receiver) { 
                if (!receiver.messages.offer(message)) { // zkus pøidat zprávu do fronty jeho zpráv
                    System.err.printf("Client %s message queue is full, dropping the message!\n", receiver.clientID);
                }
                return ;
            }
        }
    }
    
    /**
     * sendMessageToAll - Pole zprávu vem aktivním klientùm kromì sebe sama
     * 
     * @param sender  - reference odesílatele
     * @param message - øetìzec se zprávou
     */
    synchronized void sendMessageToAll(SocketHandler sender, String message) {
        for (SocketHandler handler : activeHandlersSet) // pro vechny aktivní handlery
            if (handler != sender & !Collections.disjoint(sender.joinedGroups, handler.joinedGroups)) {
                if (!handler.messages.offer(message)) // zkus pøidat zprávu do fronty jeho zpráv
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
            }
    }

    synchronized void responseToClient(String message, SocketHandler client)
    {
        if (!client.messages.offer(message)) { // zkus pøidat zprávu do fronty jeho zpráv
            System.err.printf("Client %s message queue is full, dropping the message!\n", client.clientID);
        }
    }
    /**
     * add pøidá do mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má pøidat.
     * @return true if the set did not already contain the specified element.
     */
    synchronized boolean add(SocketHandler handler) {
        return activeHandlersSet.add(handler);
    }

    /**
     * remove odebere z mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má odstranit
     * @return true if the set did not already contain the specified element.
     */
    synchronized boolean remove(SocketHandler handler) {
        return activeHandlersSet.remove(handler);
    }
}
