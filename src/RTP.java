import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The rdt sender class.
 *
 * Name: Chenguang He
 * Email: readman@iastate.edu
 * Created by chenguanghe on 9/17/14.
 */
public class RTP{
    private int timeOut; // the time of timeout
    private int MAX_QUEUE_SIZE = 9999999;
    private ArrayBlockingQueue<ArrayList<Object>> output = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE); // the large queue for message
    private ConcurrentLinkedQueue<InetSocketAddress> connection_candidate = new ConcurrentLinkedQueue<InetSocketAddress>();
    public ConcurrentHashMap<InetSocketAddress, ArrayList<ArrayList<String>>> log = new ConcurrentHashMap<InetSocketAddress, ArrayList<ArrayList<String>>>();//the queue have all packet with different state
    private DatagramSocket socket; // the socket
    private Timer timeoutTimer; // the timer to schedule timeout
    private final int RTP_PACKET_SIZE = 1000; // the size of packet
    private final int RTP_HEADER_SIZE = 28; // the size of packet
    private final int UDP_PACKET_SIZE = 2000; // the size of packet
    public ConcurrentHashMap<InetSocketAddress, ArrayList<Object>> connections = new ConcurrentHashMap<InetSocketAddress, ArrayList<Object>>();
    private final int ACK = 1; // ack
    private final int NAK = 0; // nak
    private int rcvWindow;  // receiver windows size
    private int maxRcvWindowSize;
    private int sourcePort; // the send port
    boolean ifServer;
    Thread Receive;

    /**
     * no default public constructor
     */
    private RTP() {
    }

    /**
     * the public constructor to build the sender
     * @param windowsSize the limit of windows
     * @param timeout the time of timeout
     * @param recPort the receive port
     * @param sourcePort the send port
     * @throws SocketException // socket exception
     */
    public RTP(int timeout, int rcvWindow, int sourcePort, int destinationPort, InetAddress destIPaddress, boolean ifServer) throws SocketException {
        int windowsSize = (int) Math.ceil(rcvWindow/RTP_PACKET_SIZE);
    	this.rcvWindow = windowsSize;
    	maxRcvWindowSize = windowsSize;
        this.timeOut = timeout;
        //windows = new AtomicIntegerArray(maxSenderWindowSize.get());
        if(ifServer){
        	this.socket = new DatagramSocket(sourcePort);
        } else {
        	this.socket = new DatagramSocket();
        }

        this.sourcePort = socket.getLocalPort();
        //this.destinationPort = destinationPort;
        //this.destIPaddress = destIPaddress;
        this.ifServer = ifServer;
        this.timeoutTimer = new Timer(true); // sent timer
    }

    /**
     * put data to queue
     *
     * @param buf the data
     * @param len the length of data
     */
    public void pushToQueue(byte[] data, int destinationPort, InetAddress destIPaddress, int seq, int ifFin) {
        try {
        	InetSocketAddress socketAddress = new InetSocketAddress(destIPaddress, destinationPort);
        	if(connections.containsKey(socketAddress)){
        		ArrayList<Object> windowConnection = connections.get(socketAddress);
        		ArrayBlockingQueue<DatagramPacket> queue = (ArrayBlockingQueue<DatagramPacket>) windowConnection.get(8);
	            RTPPacket rtppacket = new RTPPacket(this.sourcePort, destinationPort, data, this.rcvWindow);
	            rtppacket.getHeader().setSequenceNumber(seq);
	            windowConnection.set(11, new AtomicInteger(seq));
	            boolean fin = (ifFin == 1) ? true : false;
	            rtppacket.getHeader().setFIN(fin);
	            rtppacket.updateChecksum();
	            //state omitted
	            queue.put(RTP2UDP(rtppacket, destIPaddress, destinationPort));
	            write(new InetSocketAddress(destIPaddress, destinationPort), seq, "Send: In Queue");
        	} else {
        		System.out.println("Not connected");
        		}
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void connectionSetup(int destinationPort, InetAddress destIPaddress) throws Exception {
        RTPHeader header = new RTPHeader(this.sourcePort, destinationPort, 0, this.rcvWindow);
        header.setSYN(true);
        RTPPacket rtpp = new RTPPacket(header, null);
        rtpp.updateChecksum();
        byte[] syndata = rtpp.getPacketByteArray();
        DatagramPacket synPacket = new DatagramPacket(syndata, syndata.length, destIPaddress, destinationPort);
        socket.send(synPacket);
    }
    
    public DatagramPacket RTP2UDP(RTPPacket rtppacket, InetAddress destIPaddress, int destinationPort){
    	byte[] packetData = rtppacket.getPacketByteArray();
        return new DatagramPacket(packetData, packetData.length, destIPaddress, destinationPort);
    }
    
    public RTPPacket UDP2RTP(DatagramPacket udppacket) {
    	byte[] packetbyte = udppacket.getData();
        RTPPacket rtppacket = new RTPPacket(packetbyte);       
        return rtppacket;
    }
    
    /**
     * write log
     * @param seq // the seq number
     * @param s the log
     */
    public void write(InetSocketAddress socketAddress, int seq, String msg) {
    	String message = "Host/Port: " + socketAddress + ", Sequence Number: " + seq + ", State: " + msg;
        if (log.containsKey(socketAddress)){
            ArrayList<ArrayList<String>> sequence = log.get(socketAddress);
            if(seq>=sequence.size()){
                ArrayList<String> msgsequence = new ArrayList<String>();
                msgsequence.add(msg);
                //sequence.add(seq, msgsequence);
            } else {
                ArrayList<String> msgsequence = sequence.get(seq);
                msgsequence.add(message);
            }
        } else {
            ArrayList<String> msgsequence = new ArrayList<String>();
            msgsequence.add(message);
            ArrayList<ArrayList<String>> sequence = new ArrayList<ArrayList<String>>();
            sequence.add(seq, msgsequence);
            log.put(socketAddress, sequence);
        }
    }
    
    /**
     * return if it is blocked
     * @return true it is in block, otherwise false;
     */
    public ConcurrentHashMap<InetSocketAddress, ArrayList<ArrayList<String>>> getLog() {
        return this.log;
    }
 
    /**
     * return if it is blocked
     * @return true it is in block, otherwise false;
     */
    public ConcurrentHashMap<InetSocketAddress, ArrayList<Object>> getConnections() {
        return this.connections;
    }
 
    public ArrayBlockingQueue<ArrayList<Object>> getoutPut() {
        return this.output;
    }
 
/*    public void startSend(InetSocketAddress socketAddress) {
    }*/
    public void startReceive() {
    	Receive = new Thread(new Receive());
    	Receive.start();
    }
    
    public int getsourcePort(){
    	return this.sourcePort;
    }
    
    public int pushFiletoQueue(String postfilename, int destinationPort, InetAddress destIPaddress, int startSeq) throws IOException{

    	byte[] array = Files.readAllBytes(new File(postfilename).toPath());
    	int RTP_PAYLOAD_SIZE = RTP_PACKET_SIZE - RTP_HEADER_SIZE;
		System.out.println("Total length: " + array.length);
		
		
		int offset = 0;
		int packetCounter = 0;
		while (offset < array.length) {
			byte[] outputBytes;
			
			if(array.length - offset < RTP_PAYLOAD_SIZE ) {
				outputBytes = new byte[array.length - offset];
				System.arraycopy(array, offset, outputBytes, 0, array.length - offset);
				pushToQueue(outputBytes, destinationPort, destIPaddress, startSeq++, 1);
				packetCounter++;
				break;
			}
			
			outputBytes = new byte[RTP_PAYLOAD_SIZE];
			System.arraycopy(array, offset, outputBytes, 0, RTP_PAYLOAD_SIZE);
			offset += RTP_PAYLOAD_SIZE ;
			if(array.length - offset == RTP_PAYLOAD_SIZE ) {
				pushToQueue(outputBytes, destinationPort, destIPaddress, startSeq++, 1);
				packetCounter++;
			} else {
				pushToQueue(outputBytes, destinationPort, destIPaddress, startSeq++, 0);	
				packetCounter++;
			}
		}
		System.out.println("Total Packet: " + packetCounter);
		return startSeq;
		
    }
 
    

    
    public void disconnect(InetSocketAddress socketAddress){
    	connections.remove(socketAddress);  	
    }
   
    private void Send(DatagramPacket udppacket, InetSocketAddress socketAddress) throws Exception {
        socket.send(udppacket);
        timeoutTimer.schedule(new PacketTimeout(udppacket, socketAddress), timeOut); // when send a packet, set a timer as well
    }

    /**
     * the send method which in a new thread to put data into queue
     */
    private class Send implements Runnable {
    	private InetSocketAddress socketAddress;
    	
    	public Send(InetSocketAddress socketAddress){
    		this.socketAddress = socketAddress;
    	}

        public void run() {
            //windowSize = new AtomicInteger(0);
            //windows = new AtomicIntegerArray(windowSize.intValue());
            //windowSize = 0; //size of windows
            while (true) {
    /*            while (queue.isEmpty()&&windowSize == 0) {
                    isBlock = false;
                }*/			
            		ArrayList<Object> windowConnection = connections.get(socketAddress);
        			((Lock) windowConnection.get(9)).lock();
            		AtomicInteger windowSize = (AtomicInteger) windowConnection.get(5);
	        		AtomicIntegerArray windows = (AtomicIntegerArray) windowConnection.get(6);
	        		ConcurrentLinkedQueue<DatagramPacket> WindowsList = (ConcurrentLinkedQueue<DatagramPacket>) windowConnection.get(7);
	        		ArrayBlockingQueue<DatagramPacket> queue = (ArrayBlockingQueue<DatagramPacket>) windowConnection.get(8);
	        		AtomicInteger maxSenderWindowSize = (AtomicInteger) windowConnection.get(10);
		            	if(!queue.isEmpty()){
	            			//System.out.println("wrong1");
			    	            if (windowSize.get() == 0) { // if it is the first time to send
			    	            	/*try {
										Thread.sleep(200);
									} catch (InterruptedException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}*/
			    	            	//System.out.println("aaaaaaa" + windowSize+"aaaaaaa" + queue.size());
			    	            		windowSize.set(Math.min(queue.size(), maxSenderWindowSize.get()));
			    	            		windows = new AtomicIntegerArray(windowSize.get());
			    	            		for(int i=0;i<windows.length();i++){
			    	            			windows.set(i, NAK);
			    	            		}

			    	                for (int i = 0; i < windowSize.get(); i++) {
			    	                	DatagramPacket udppacket = queue.poll();
			    	                	if(udppacket != null){
					    	            		WindowsList.add(udppacket);
			    		                    try {
			    		                    	write(new InetSocketAddress(udppacket.getAddress(), udppacket.getPort()), UDP2RTP(udppacket).getHeader().getSequenceNumber(), "Send: Initial window sent");
			    								Send(udppacket,socketAddress);
			    					            	//System.out.println(queue.size());	    								
			    							} catch (Exception e) {
			    								// TODO Auto-generated catch block
			    								e.printStackTrace();
			    							}			    		                    
			    	                	}                   
			    	                }
			    	            } else {
		    						//System.out.println("aaaaaaaaaaaaaaaaaaaaa "+ windows.length());			    	            	
					            	//System.out.println(queue.size());

			    	                int emptySpace = 0;
			    					try {
			    						//System.out.println(windows.length());
			    				        //Thread.sleep(100);
			    					        for (int i = 0; i < windows.length(); i++) {
			    					        	
			    						            if (windows.get(i) == ACK) {
			    						            	emptySpace++;
			    						            	System.out.println("adjust "+emptySpace +" "+ windows.length() + " " + windowSize.get());
			    						            } else {
			    						            	//System.out.println("warning");
			    						                break;
			    						            }

			    					        }
			    						//System.out.println(emptySpace);

			    					} catch (Exception e) {
			    						// TODO Auto-generated catch block
			    						e.printStackTrace();
			    					}
			    	                int[] newWindows = new int[windowSize.get()];
			    	                int ping = 0; // the variable to set windows
			    	                //adjust list of sending windows
			    	                for (int i = 0; i < emptySpace; i++) {
			    	                	
				    	            		WindowsList.poll();
				    	                	System.out.println("merge "+windowSize.get()+ " " + emptySpace + " " + WindowsList.size());
			    	                }
			    	                // merge to new windows
			    	                for (int i = emptySpace; i < Math.min(windows.length(), windowSize.get()); i++) {
			    	                	//System.out.println("merge "+windowSize.get()+ " " + emptySpace + " " + i );
			    	                    newWindows[ping] = windows.get(i);
			    	                    ping++;
			    	                }			    	               
			    	                
			    	                // send new packet
			    	                while (emptySpace != 0 && !queue.isEmpty()) {
			    	                	emptySpace = emptySpace -1;
			    	                	DatagramPacket udppacket = queue.poll();
				    	            		WindowsList.add(udppacket);
			    	                    try {
			    	                    	write(new InetSocketAddress(udppacket.getAddress(), udppacket.getPort()), UDP2RTP(udppacket).getHeader().getSequenceNumber(), "Send: sent");
			    							Send(udppacket, socketAddress);		    							
			    						} catch (Exception e) {
			    							// TODO Auto-generated catch block
			    							e.printStackTrace();
			    						}		    	                    
			    	                }
			    	                //System.out.println("merge "+windowSize.get()+ " " + emptySpace + " " + newWindows.length);
			    	                windows = new AtomicIntegerArray(newWindows);
			    	                // merge windows

			            			//System.out.println(WindowsList.size());
			    	            	windowSize.set(WindowsList.size());

			    	            }
/*			                	if(windowSize.intValue() == 0){
			                		System.out.println("cccc");
			    	                windowSize.set(Math.min(queue.size(), maxWindowsSize));
			                	}*/
			            	
				            	if(windowSize.get() == 0 || windowSize.get()< maxSenderWindowSize.get()){
				            		windowSize.set(Math.min(queue.size(), maxSenderWindowSize.get()));
				            	}
		    	                windowConnection.set(5, windowSize);
		    	                windowConnection.set(6, windows);
		    	                windowConnection.set(7, WindowsList);
		    	                windowConnection.set(8, queue);

		            		} else {
			            	}
	        			((Lock) windowConnection.get(9)).unlock();
		            	//System.out.println(queue.size());
		            	//windowSize.set(Math.min(queue.size(), maxWindowsSize));           	
            }
        }       
    }

    /**
     * the send method which in a new thread to put data into queue
     */
    private class Receive implements Runnable {

        public void run() {
        	while(true){
	            byte[] rcvpkt = new byte[UDP_PACKET_SIZE];
	            DatagramPacket rcvpacket = new DatagramPacket(rcvpkt, rcvpkt.length);
	            try {

	            		socket.receive(rcvpacket);
	    		} catch (IOException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();
	    		}      
	            RTPPacket rtppacket = UDP2RTP(rcvpacket);
	            int checksum = rtppacket.calculateChecksum();
	            if(rtppacket.calculateChecksum() == rtppacket.getHeader().getChecksum()){
	    	        int seq = rtppacket.getHeader().getSequenceNumber();
	    	        int fromPort = rtppacket.getHeader().getSourcePort();
	    	        InetAddress sourceIP = rcvpacket.getAddress();
	    	        int ack = rtppacket.getHeader().isACK() ? 1 : 0;
	    	        int syn = rtppacket.getHeader().isSYN() ? 1 : 0;
	    	        int fin = rtppacket.getHeader().isFIN() ? 1 : 0;
	    	        System.out.println(ack +" " + syn + " " + fin);
	    			if(rtppacket.getHeader().isSYN() && rtppacket.getHeader().isACK()){
	    		        //write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: connection Complete"); 
	    		        InetSocketAddress socketAddress = new InetSocketAddress(sourceIP, fromPort);
		            		if(!connections.containsKey(socketAddress)){
		    					Integer startWindow = 0;
		    					Integer[] windows_ack = new Integer[maxRcvWindowSize];
		    	                Arrays.fill(windows_ack, NAK);
		    	                ArrayBlockingQueue<DatagramPacket> buffer_rcv = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
		    	                Integer ifFIN = null;
		    	                AtomicInteger numberOfTimeouts =  new AtomicInteger(0);
		    	                AtomicInteger windowSize = new AtomicInteger(0);
		    	                AtomicIntegerArray windows = new AtomicIntegerArray(windowSize.get());
		    	                ConcurrentLinkedQueue<DatagramPacket> WindowsList = new ConcurrentLinkedQueue<DatagramPacket>();
		    	                ArrayBlockingQueue<DatagramPacket> queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE); // the large queue for message
		    	                Lock lock = new ReentrantLock();
		    	                AtomicInteger maxSenderWindowSize = new AtomicInteger(0);
		    	                maxSenderWindowSize.set(rtppacket.getHeader().getRcvWindow());
		    	                AtomicInteger sendSeq = new AtomicInteger(-1);
		    	            	Thread Send = new Thread(new Send(socketAddress));
		    	                ArrayList<Object> windowConnection = new ArrayList<Object>();
		    	        		windowConnection.add(startWindow);
		    	        		windowConnection.add(windows_ack);	
		    	        		windowConnection.add(buffer_rcv);
		    	        		windowConnection.add(ifFIN);
		    	        		windowConnection.add(numberOfTimeouts);
		    	        		windowConnection.add(windowSize);
		    	        		windowConnection.add(windows);
		    	        		windowConnection.add(WindowsList);
		    	        		windowConnection.add(queue);
		    	        		windowConnection.add(lock);
		    	        		windowConnection.add(maxSenderWindowSize);
		    	        		windowConnection.add(sendSeq);
			    		        RTPHeader header = new RTPHeader(sourcePort, fromPort, 0, rcvWindow);
			    		        header.setACK(true);
			    		        RTPPacket rtpp = new RTPPacket(header, null);
			    		        rtpp.updateChecksum();
			    		        byte[] ackdata = rtpp.getPacketByteArray();
			    		        DatagramPacket ackPacket = new DatagramPacket(ackdata, ackdata.length, sourceIP, fromPort);
			    		        try {

			    	            		socket.send(ackPacket);
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
		    	            	Send.start();
		    	        		windowConnection.add(Send);
		            			connections.put(socketAddress, windowConnection);
		            		}
	            		continue;
	    			} else if(rtppacket.getHeader().isSYN()){
	    	            //send syn-ack
	                    RTPHeader header = new RTPHeader(sourcePort, fromPort, 0, rcvWindow);
	                    header.setACK(true);
	                    header.setSYN(true);
	                    RTPPacket rtpp = new RTPPacket(header, null);
	                    rtpp.updateChecksum();
	                    byte[] synackData = rtpp.getPacketByteArray();
	                    DatagramPacket synackPacket = new DatagramPacket(synackData, synackData.length, sourceIP, fromPort);
	                    try {
	    	            		socket.send(synackPacket);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

    	            	connection_candidate.add(new InetSocketAddress(sourceIP, fromPort));
	                    continue;
	    	        } else if(ack == ACK && connection_candidate.contains(new InetSocketAddress(sourceIP, fromPort))){
		    	        	InetSocketAddress socketAddress = new InetSocketAddress(sourceIP, fromPort);
			            		if(!connections.containsKey(socketAddress)){
			    					Integer startWindow = 0;
			    					Integer[] windows_ack = new Integer[maxRcvWindowSize];
			    	                Arrays.fill(windows_ack, NAK);
			    	                ArrayBlockingQueue<DatagramPacket> buffer_rcv = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
			    	                Integer ifFIN = null;
			    	                AtomicInteger numberOfTimeouts =  new AtomicInteger(0);
			    	                AtomicInteger windowSize = new AtomicInteger(0);
			    	                AtomicIntegerArray windows = new AtomicIntegerArray(windowSize.intValue());
			    	                ConcurrentLinkedQueue<DatagramPacket> WindowsList = new ConcurrentLinkedQueue<DatagramPacket>();
			    	                ArrayBlockingQueue<DatagramPacket> queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE); // the large queue for message
			    	                Lock lock = new ReentrantLock();
			    	                AtomicInteger maxSenderWindowSize = new AtomicInteger(0);
			    	                maxSenderWindowSize.set(rtppacket.getHeader().getRcvWindow());
			    	                AtomicInteger sendSeq = new AtomicInteger(-1);
			    	            	Thread Send = new Thread(new Send(socketAddress));
			    	                ArrayList<Object> windowConnection = new ArrayList<Object>();
			    	        		windowConnection.add(startWindow);
			    	        		windowConnection.add(windows_ack);	
			    	        		windowConnection.add(buffer_rcv);
			    	        		windowConnection.add(ifFIN);
			    	        		windowConnection.add(numberOfTimeouts);
			    	        		windowConnection.add(windowSize);
			    	        		windowConnection.add(windows);
			    	        		windowConnection.add(WindowsList);
			    	        		windowConnection.add(queue);
			    	        		windowConnection.add(lock);
			    	        		windowConnection.add(maxSenderWindowSize);
			    	        		windowConnection.add(sendSeq);
			    	            	Send.start();
			    	        		windowConnection.add(Send);
			            			connections.put(socketAddress, windowConnection);
			            		}
		            		connection_candidate.remove(new InetSocketAddress(sourceIP, fromPort));
		            		continue;
    	        	} else if(ack == ACK) { // if it acked
    	        		System.out.println("kkkkkk");
		            			InetSocketAddress socketAddress = new InetSocketAddress(sourceIP, fromPort);
				    	        	if(connections.containsKey(socketAddress)) {
				    	        		ArrayList<Object> windowConnection = connections.get(socketAddress);
			    	        			((Lock) windowConnection.get(9)).lock();
				    	        		AtomicInteger windowSize = (AtomicInteger) windowConnection.get(5);
				    	        		AtomicIntegerArray windows = (AtomicIntegerArray) windowConnection.get(6);
				    	        		ConcurrentLinkedQueue<DatagramPacket> WindowsList = (ConcurrentLinkedQueue<DatagramPacket>) windowConnection.get(7);
				    	        		AtomicInteger maxSenderWindowSize = (AtomicInteger) windowConnection.get(10);
			    	        			System.out.println("000 "+windowSize+" "+WindowsList.size()+" "+windows.length()+" "+seq);
				    	        		//if(windowSize.get() != 0||windowSize.get() == 0 && fin == 1){
					    	        		System.out.println("aaa "+windowSize+" "+WindowsList.size()+" "+windows.length()+" "+seq);
					    	        		int index = 0;
						    			        for (DatagramPacket udpp: WindowsList) {
						    			        	RTPPacket rtpp = UDP2RTP(udpp);
						    			        	if(rtpp.getHeader().getSequenceNumber() == seq && rtpp.getHeader().getDestinationPort() == fromPort 
						    			        			&& udpp.getAddress().equals(sourceIP)){
						    			        		System.out.println("bbb "+windowSize+" "+WindowsList.size()+" "+windows.length()+" "+seq);
						    			        			if(windows.length()> index){
						    			        				System.out.println("kkkkkk2");
							    			        			if(windows.get(index) == ACK){
								    			        			write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: Duplicate ACK Packet");
								    			        		} else {
								    			        			System.out.println("ccc "+windowSize+" "+WindowsList.size()+" "+windows.length()+" "+seq+" "+rtpp.getHeader().getSequenceNumber()+" "+index);
								    				        		write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: ACK Packet");
								    			        			
								    				        		maxSenderWindowSize.set(rtpp.getHeader().getRcvWindow());
								    				        		
								    				        		windows.set(index, ACK);
								    			        			System.out.println("nnn"+windows.get(index));
	/*							    			        			for(int i=0;i<windows.length();i++){
								    			        				System.out.println("nnn"+windows.get(i));
								    			        			}
								    			        			System.out.println("nnnend");*/
								    				                 if(fin == 1){
								    				                	 write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: FIN ACK Packet");
								    				                	 windowConnection.set(3, null);
								    				                	 int emptySpace = 0;
								    				    					try {
								    				    				        //Thread.sleep(100);
								    				    					        for (int i = 0; i < windows.length(); i++) {
								    				    					        	
								    				    						            if (windows.get(i) == ACK) {
								    				    						            	emptySpace++;
								    				    						            	System.out.println("adjust "+emptySpace +" "+ windows.length() + " " + windowSize.get());
								    				    						            } else {
								    				    						            	//System.out.println("warning");
								    				    						                break;
								    				    						            }
	
								    				    					        }
								    				    						//System.out.println(emptySpace);
	
								    				    					} catch (Exception e) {
								    				    						// TODO Auto-generated catch block
								    				    						e.printStackTrace();
								    				    					}
								    				    	                int[] newWindows = new int[windowSize.get()];
								    				    	                int ping = 0; // the variable to set windows
								    				    	                //adjust list of sending windows
								    				    	                for (int i = 0; i < emptySpace; i++) {
								    				    	                	
								    					    	            		WindowsList.poll();
								    					    	                	System.out.println("merge "+windowSize.get()+ " " + emptySpace + " " + WindowsList.size());
								    				    	                }
								    				    	                // merge to new windows
								    				    	                for (int i = emptySpace; i < Math.min(windows.length(), windowSize.get()); i++) {
								    				    	                	//System.out.println("merge "+windowSize.get()+ " " + emptySpace + " " + i );
								    				    	                    newWindows[ping] = windows.get(i);
								    				    	                    ping++;
								    				    	                }			    	               
								    				    	                
								    				    	                // send new packet
	/*							    				    	                while (emptySpace != 0 && !queue.isEmpty()) {
								    				    	                	emptySpace = emptySpace -1;
								    				    	                	DatagramPacket udppacket = queue.poll();
								    					    	            		WindowsList.add(udppacket);
								    				    	                    try {
								    				    	                    	write(new InetSocketAddress(udppacket.getAddress(), udppacket.getPort()), UDP2RTP(udppacket).getHeader().getSequenceNumber(), "Send: sent");
								    				    							Send(udppacket);		    							
								    				    						} catch (Exception e) {
								    				    							// TODO Auto-generated catch block
								    				    							e.printStackTrace();
								    				    						}		    	                    
								    				    	                }*/
								    				    	                //System.out.println("merge "+windowSize.get()+ " " + emptySpace + " " + newWindows.length);
								    				    	                windows = new AtomicIntegerArray(newWindows);
								    				    	                // merge windows
	
								    				            			//System.out.println(WindowsList.size());
								    				    	            	windowSize.set(WindowsList.size());
								    				                	
								    				                	 //ifFinish = true;
								    				                 }
								    			        		}
						    			        			}
						    			        			//lock.unlock();
						    		        			break;
						    			        	} else if(rtpp.getHeader().getSequenceNumber() < seq && rtpp.getHeader().getSourcePort() == fromPort 
						    			        			&& udpp.getAddress().equals(sourceIP)){
						    			        		write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: Duplicate ACK Packet");
						    			        	} else {
						    			        		write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: ACK Packet out of window");
						    			        	}
						    			        	index++;
					            			}
/*				    	        		} else {
					    	        		System.out.println("!!!!!!!!!!!!!!!!!!!!! " + windowSize);
				    	        		}*/
				    	                windowConnection.set(5, windowSize);
				    	                windowConnection.set(6, windows);
				    	                windowConnection.set(7, WindowsList);
	    				        		windowConnection.set(10, maxSenderWindowSize);
			    	        			((Lock) windowConnection.get(9)).unlock();
				    	        	} else {
				    	        		write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: No connection Setup");
				    	        	}

			    	        	continue;
		            } else if(ack == NAK) {
		            	System.out.println("NAK");
	            			InetSocketAddress socketAddress = new InetSocketAddress(sourceIP, fromPort);
			    	        	if(connections.containsKey(socketAddress)){
			    	        		ArrayList<Object> windowConnection = connections.get(socketAddress);
			    	        		Integer startWindow = (Integer) windowConnection.get(0);
			    	        		Integer[] windows_ack = (Integer[]) windowConnection.get(1);
			    					ArrayBlockingQueue<DatagramPacket> buffer_rcv = (ArrayBlockingQueue<DatagramPacket>) windowConnection.get(2);
			    	        		try {
										buffer_rcv.put(rcvpacket);
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
			    	        		if (startWindow <= seq) {
			    		                if (seq - startWindow < maxRcvWindowSize) {
			    		                	windows_ack[seq - startWindow] = ACK;		                	
			    		                	//System.out.println("Receive: Data Packet Received");
			    		                } else {
			    		                	System.out.println("Receive: Data Packet out of window" + startWindow + " " + seq);
			    		                	continue;
			    		                }
			    		            } else if(startWindow > seq) {
			    		            	System.out.println("Receive: Duplicate Data Packet " + startWindow + " " + seq);
			    	            		continue;
			    		            }
			    	        		
			    	        		//calculate the dynamic receiver's window
			    	        		int lastAck = 0;
			    	        		for (int i = 0; i < maxRcvWindowSize; i++){
			    	        			if (windows_ack[i] == ACK){
			    	        				lastAck = i;
			    	        			}
			    	        		}
			    	        		rcvWindow = maxRcvWindowSize - lastAck;
			    	        		
			    		            //send ack
			    	                 RTPHeader header = new RTPHeader(sourcePort, fromPort, seq, rcvWindow);
			    	                 header.setACK(true);
			    	                 if(fin == 1){
			    	                	 windowConnection.set(3, seq);
			    	                	 header.setFIN(true);
			    	                	 //ifFinish = true;
			    	                 }
			    	                 int index = 0;
			    	                 Integer Fin_seq = (Integer) windowConnection.get(3);
			    	                 if(Fin_seq != null){
				    	                 for(int i=0; i<Fin_seq-startWindow+1;i++){
				    	                	 if(windows_ack[i] == 0){
				    	                		 break;
				    	                	 }
				    	                	 index++;
				    	                 }
				    	                 //data ready
				    	                 if(index ==Fin_seq-startWindow+1){
				    	                	 System.out.println("data ready");
				    	                	 ArrayList<Object> output_arr = new ArrayList<>();
				    	                	 output_arr.add(socketAddress);
				    	                	 output_arr.add(buffer_rcv);
				    	                	 try {
				    		            			output.put(output_arr);
											} catch (InterruptedException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
				    	                	 windowConnection.set(3, null);
				    	                	 buffer_rcv = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
				    	                	 windowConnection.set(2, buffer_rcv);
				    	                 }
			    	                 }
			    	                 RTPPacket rtpp = new RTPPacket(header, null);
			    	                 rtpp.updateChecksum();
			    	                 byte[] ackData = rtpp.getPacketByteArray();
			    	                 DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, sourceIP, fromPort);
			    	                 try {
			    			            	System.out.println("ACK");
			    	                		 socket.send(ackPacket);
				    		                 write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Send: ACK Packet");
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
			    	                 //shift window
			    	                 while (true) {
			    	                     if (windows_ack[0] == ACK) {
			    	                         for (int i = 0; i < maxRcvWindowSize - 1; i++) {
			    	                        	 windows_ack[i] = windows_ack[i + 1];
			    	                         }
			    	                         windows_ack[maxRcvWindowSize - 1] = NAK;
			    	                         startWindow++;
			    	                         windowConnection.set(0, startWindow);
			    	                     } else {
			    	                         break;
			    	                     }
			    	                 }
			    	                 connections.put(socketAddress, windowConnection);
			    	        	} else {
			    	        		write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: No connection Setup");
			    	        	}
	            		} else {
	            			write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: Duplicate Packets");
	            		}
	            } else {
	            	write(new InetSocketAddress(rcvpacket.getAddress(), rcvpacket.getPort()), rtppacket.getHeader().getSequenceNumber(), "Receive: Packet Corrupt");
	            }        
	        }
        
        }
    }
    
/*    *//**
     * the method will move the first nak in windows to the first position.
     * @return the number of shifts
     * @throws Exception the exception
     *//*
    private int adjustWindow() throws Exception {
        //Thread.sleep(100);
        int windowMoved = 0;
	        for (int i = 0; i < windows.length(); i++) {
	        	
		            if (windows.get(i) == ACK) {
		                windowMoved++;
		            	System.out.println("adjust"+windowMoved +" "+ windows.length());
		            } else {
		            	//System.out.println("warning");
		                break;
		            }

	        }
        return windowMoved;
    }*/

    /**
     * the timer for packet use to set up the timeout.
     */
    private class PacketTimeout extends TimerTask {
        private DatagramPacket p;
        private InetSocketAddress socketAddress;

        public PacketTimeout(DatagramPacket p, InetSocketAddress socketAddress) {
            this.p = p;
            this.socketAddress = socketAddress;
        }
        
        public void run() {
            try {
            	ArrayList<Object> windowConnection = connections.get(socketAddress);
            	AtomicInteger numberOfTimeouts =  (AtomicInteger) windowConnection.get(4);
            	ConcurrentLinkedQueue<DatagramPacket> WindowsList = (ConcurrentLinkedQueue<DatagramPacket>) windowConnection.get(7);
                if (WindowsList.contains(p)) {
                    //if packet has not been ACKed
                    numberOfTimeouts.incrementAndGet();
                    Send(p,socketAddress);
                    //write(p.getHeader().getSequenceNumber(), "Resent");
                }
                windowConnection.set(4, numberOfTimeouts);
            } catch (Exception e) {
            }
        }
    }

}