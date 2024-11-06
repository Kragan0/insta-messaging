package utb.fai;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.ArrayList;


public class SocketHandler {
	
	String username = "";
	ArrayList<String> joinedGroups = new ArrayList<String>();
	
	/** mySocket je socket, o který se bude tento SocketHandler starat */
	Socket mySocket;

	/** client ID je øetìzec ve formátu <IP_adresa>:<port> */
	String clientID;

	/**
	 * activeHandlers je reference na mnoinu vech právì bìících SocketHandlerù.
	 * Potøebujeme si ji udrovat, abychom mohli zprávu od tohoto klienta
	 * poslat vem ostatním!
	 */
	ActiveHandlers activeHandlers;

	/**
	 * messages je fronta pøíchozích zpráv, kterou musí mít kaý klient svoji
	 * vlastní - pokud bude je pøetíená nebo nefunkèní klientova sí,
	 * èekají zprávy na doruèení právì ve frontì messages
	 */
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20);

	/**
	 * startSignal je synchronizaèní závora, která zaøizuje, aby oba tasky
	 * OutputHandler.run() a InputHandler.run() zaèaly ve stejný okamik.
	 */
	CountDownLatch startSignal = new CountDownLatch(2);

	/** outputHandler.run() se bude starat o OutputStream mého socketu */
	OutputHandler outputHandler = new OutputHandler();
	/** inputHandler.run() se bude starat o InputStream mého socketu */
	InputHandler inputHandler = new InputHandler();
	/**
	 * protoe v outputHandleru nedovedu detekovat uzavøení socketu, pomùe mi
	 * inputFinished
	 */
	volatile boolean inputFinished = false;

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) throws IOException {
		this.mySocket = mySocket;
		clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		this.activeHandlers = activeHandlers;

		// Public miestnost kde sa kazdy joine
		joinedGroups.add("public");
		
		// Pri spusteni uzivatel zada svoje meno
		BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));
		OutputStreamWriter writer = new OutputStreamWriter(mySocket.getOutputStream(),"UTF-8");
		String newUsername;
		
		writer.write("Please enter your name: ");
		writer.flush();			
		
		while ((newUsername = reader.readLine()).equals("") || activeHandlers.usernameExists(newUsername)) {
			writer.write("Incorrect or already taken username, enter a different name: ");
			writer.flush();			
		}
		username = newUsername;

	}

	class OutputHandler implements Runnable {
		public void run() {
			OutputStreamWriter writer;
			try {
				System.err.println("DBG>Output handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Output handler running for " + clientID);
				writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
				writer.write("\nYou are connected from " + clientID + "\n");
				writer.flush();
				while (!inputFinished) {
					String m = messages.take();// blokující ètení - pokud není ve frontì zpráv nic, uspi se!
					writer.write(m + "\r\n"); // pokud nìjaké zprávy od ostatních máme,
					writer.flush(); // poleme je naemu klientovi
					System.err.println("DBG>Message sent to " + clientID + ":" + m + "\n");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.err.println("DBG>Output handler for " + clientID + " has finished.");

		}
	}

    class InputHandler implements Runnable {

        public void run() {
            try {
                System.err.println("DBG>Input handler starting for " + clientID);
                startSignal.countDown();
                startSignal.await();
                System.err.println("DBG>Input handler running for " + clientID);
                String message = "";
                /**
                 * v okamiku, kdy nás Thread pool spustí, pøidáme se do mnoiny
                 * vech aktivních handlerù, aby chodily zprávy od ostatních i
                 * nám
                 */
                activeHandlers.add(SocketHandler.this);
                BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
                while ((message = reader.readLine()) != null) { // pøila od mého klienta nìjaká zpráva?

                    // Prikazy ktoré uživatel má k dispozicií
                    if (message.charAt(0) == '#') {
                        String[] parts = message.split(" ", 3);
                        String command = parts[0];
                        String response="";

                        switch (command) {
                            case "#setMyName" -> {
                                // Zadali meno?
                                if (parts.length != 2) {
                                    response = "[Error] >> Syntax error: #setMyName <name>";
                                    System.out.println(response);
                                    activeHandlers.responseToClient(response, SocketHandler.this);
                                    continue;
                                }
                                // Je to unikatne?
                                if (activeHandlers.usernameExists(parts[1])) {
                                    response = "[Error] >> This username is already taken.";
                                    System.out.println(response);
                                    activeHandlers.responseToClient(response, SocketHandler.this);
                                    continue;
                                } 
                                
                                SocketHandler.this.username = parts[1];
                                response = "[Info] >> Your name was set to: " + parts[1];
                                System.out.println(response);
                                activeHandlers.responseToClient(response, SocketHandler.this);
                                continue;
                            }
                            case "#sendPrivate" -> {
                                
                                if (parts.length < 3) {
                                    response = "[Error] >> Syntax error: #sendPrivate <name> <message>";
                                    System.out.println(response);
                                    activeHandlers.responseToClient(response, SocketHandler.this);
                                    continue;
                                }
                                // prijmatiel existuje?!
                                if (activeHandlers.usernameExists(parts[1])) {
                                    message = "[" + SocketHandler.this.username + "] >> " + parts[2];
                                    activeHandlers.sendPrivateMessage(parts[1], message);
                                } else {
                                    response = "[Error] >> User " + parts[1] + " not found.";
                                    System.out.println(response);
                                    activeHandlers.responseToClient(response, SocketHandler.this);
                                }
                                continue;
                            }
                            case "#join" -> {
                                
                                if (parts.length != 2) {
                                    response = "[Error] >> Syntax error: #join <title>";
                                } else if (!parts[1].contains(" ")) { // Ensure no spaces in the group name
                                    SocketHandler.this.joinedGroups.add(parts[1]);
                                    response = "[Info] >> Joined group: " + parts[1];
                                } else {
                                    response = "[Error] >> Group name cannot contain spaces.";
                                }
                                System.out.println(response);
                                activeHandlers.responseToClient(response, SocketHandler.this);
                                continue;
                            }
                            case "#leave" -> {
                                
                                if (parts.length != 2) {
                                    response = "[Error] >> Syntax error: #leave <title>";
                                } 
                                // Super ze to vracia bool, hodila by sa to VEDIET SKOR
                                else if (!SocketHandler.this.joinedGroups.remove(parts[1])) {
                                    response = "[Error] >> You are not a member of the group: " + parts[1];
                                } else {
                                    response = "[Info] >> Left group: " + parts[1];
                                }
                                System.out.println(response);
                                activeHandlers.responseToClient(response, SocketHandler.this);
                                continue;
                            }
                            case "#groups" -> {
                                if (parts.length > 1) {
                                    response = "[Error] >> #groups command does not take any arguments.";
                                } else { 
                                    response = "[Info] >> Your groups: " + String.join(", ", SocketHandler.this.joinedGroups);
                                }
                                System.out.println(response);
                                activeHandlers.responseToClient(response, SocketHandler.this);
                                continue;
                            }

                            default -> {
                            }
                        }
                    }
					// Pokial sa nejedna o prikaz
					else {
                    // ano - poli ji vem ostatním klientùm
                    message = "["+ SocketHandler.this.username +"]" + " >> " + message;
                    System.out.println(message);
                    activeHandlers.sendMessageToAll(SocketHandler.this, message);
					}
                }
                inputFinished = true;
                messages.offer("OutputHandler, wakeup and die!");
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                // remove yourself from the set of activeHandlers
                synchronized (activeHandlers) {
                    activeHandlers.remove(SocketHandler.this);
                }
            }
            System.err.println("DBG>Input handler for " + clientID + " has finished.");
        }

    }
}
