package udp;

/**
 * TftpServer - a very simple TFTP like server - RC FCT/UNL
 * 
 * Limitations:
 * 		default port is not 69;
 * 		ignores mode (always works as octal);
 *              ignores all options
 *              only sends files
 * 		only sends files always assuming the default timeout
 *              data and ack blocks are a long instead of a short
 *              assumes that the sequence number and ack number is always equal
 *              to the order of the first byte (beginning at 1) of the block sent
 *              or acked
 * Note: this implementation assumes that all Java Strings used contain only
 * ASCII characters. If it's not so, lenght() and getBytes().lenght return different sizes
 * and unexpected problems can appear...
 **/

import static udp.TftpPacket.MAX_TFTP_PACKET_SIZE;
import static udp.TftpPacket.OP_ACK;
import static udp.TftpPacket.OP_DATA;
import static udp.TftpPacket.OP_ERROR;
import static udp.TftpPacket.OP_RRQ;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class FTUDPServer implements Runnable {
	static int DEFAULT_PORT = 10512; // my default port
	static int DEFAULT_TIMEOUT = 2000; // 2 sec.
	static int DEFAULT_BLOCKSIZE = 512; // default block size as in TFTP
										// RFC

	static int DEFAULT_MAX_RETRIES = 3; // default block size as in TFTP

	static final String[] ACCEPTED_OPTIONS = new String[]{"blksize", "timeout"};

	// Change it for your tests, according
	// to the block size in your client

	private int timeout = DEFAULT_TIMEOUT;
	private int blockSize = DEFAULT_BLOCKSIZE;
	private SocketAddress cltAddr;

	private String filename;
	
	private long rttStartTime;
	private long rttCount = 0;
	private long rttSum = 0;
	private long rtt = 0;
	private long rttMedian = 0;
	private int blocksCount = 0;
	
	static int DEFAULT_WINDOW_SIZE = 10; // window's size

	static final LinkedList<TftpPacket> window = new LinkedList<TftpPacket>(); // window

	FTUDPServer(TftpPacket req, SocketAddress cltAddr) {
		this.cltAddr = cltAddr;

		Map<String, String> options = req.getOptions();
		options.keySet().retainAll(Arrays.asList(ACCEPTED_OPTIONS));
		if (options.containsKey("timeout"))
			timeout = Integer.valueOf(options.get("timeout"));

		if (options.containsKey("blksize"))
			blockSize = Integer.valueOf(options.get("blksize"));

		if (options.size() > 0)
			System.out.println("Using supported options:" + options);

		filename = req.getFilename();
	}

	public void run() {
		long time = System.currentTimeMillis();
		System.out.println("INICIO!");
		sendFile(filename);
		System.out.println("FIM!");
		time = System.currentTimeMillis() - time;
		System.out.println("O envio do ficheiro demorou: "+ time +" ms");
		System.out.println("Blocos enviados e reenviados: "+ blocksCount);
		System.out.println("RTT médio: "+ rttMedian +" ms");
	}

	/**
	 * Sends an error packet
	 * 
	 * @param s - Socket to use to communicate
	 * @param err - Error number and description
	 * @param cltAddr - Destination IP and port
	 */
	private static void sendError(DatagramSocket s, int err, String str, SocketAddress cltAddr) throws IOException {
		TftpPacket pkt = new TftpPacket().putShort(OP_ERROR).putShort(err).putString(str).putByte(0);
		s.send(new DatagramPacket(pkt.getPacketData(), pkt.getLength(), cltAddr));
	}

	/**
	 * Sends a file to a client
	 * 
	 * @param file - File name to send to the client
	 * @param host - Client address
	 * @param port - Client port
	 */
	private void sendFile(String file) {
		try {
			DatagramSocket sendingSocket = new MyDatagramSocket();
			System.out.println("sending file: \"" + file + "\" to client: " + cltAddr + " from local port:" + sendingSocket.getLocalPort());
			try {
				sendingSocket.setSoTimeout(timeout);

				FileInputStream f = new FileInputStream(file);

				long count = 1; // block count starts at 1

				byte[] buffer = new byte[blockSize];
				int n = f.read(buffer); // to enter first if
				do {
					if (window.size() < DEFAULT_WINDOW_SIZE && n != -1) { // fills the window
						TftpPacket pkt = new TftpPacket().putShort(OP_DATA).putLong(count).putBytes(buffer, n);
//						System.err.println("sending:" + pkt.getByteCount() + "/size:" + pkt.getBlockData().length);

						sendingSocket.send(new DatagramPacket(pkt.getPacketData(), pkt.getLength(), cltAddr));
						rttStartTime = System.currentTimeMillis();
						window.addLast(pkt); // adds the packet to the window
						count += n; // 1, 513, 1025...
						n = f.read(buffer);
						blocksCount++;
					}
					else {
						try {
							byte[] ackBuffer = new byte[MAX_TFTP_PACKET_SIZE];
							DatagramPacket msg = new DatagramPacket(ackBuffer, ackBuffer.length);

							sendingSocket.receive(msg); // waits for ACK
							rtt = System.currentTimeMillis() -  rttStartTime;

							rttCount++;
							rttSum += rtt;
							rttMedian= rttSum / rttCount;

							TftpPacket ack = new TftpPacket(msg.getData(), msg.getLength());
//							System.out.println("got ack:" + ack.getByteCount());
							if (ack.getOpcode() == OP_ACK) {
								updateWindow(ack.getByteCount());
							}
							else
								System.err.println("error +++ (unexpected packet)");
						} catch (SocketTimeoutException e) {
							blocksCount +=  window.size();
							goBackN(sendingSocket, window, cltAddr);
						}
					}
				} while (n != -1 || window.size() > 0);

				//if the file length is a multiple of blockSize, send an empty
				//last packet...
				if (((count - 1L) % blockSize) == 0L) {
					TftpPacket pkt = new TftpPacket().putShort(OP_DATA).putLong(count).putBytes(new byte[0], 0);
					sendRecv(sendingSocket, pkt, cltAddr);
				}

				f.close();
				sendingSocket.close();

			} catch (FileNotFoundException e) {
				System.err.printf("Can't read \"%s\"\n", file);
				sendError(sendingSocket, 1, "file not found", cltAddr);
			} catch (IOException e) {
				System.err.println("Failed with error \n" + e.getMessage());
				sendError(sendingSocket, 2, e.getMessage(), cltAddr);
			}
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	/**
	 * Sends a datagram and waits for an ACK (with timeout and retransmission)
	 * 
	 * @param sock - Socket to use to communicate
	 * @param blk - DatagramPacket to send
	 * @param dst - Must include destination IP and port
	 * @throws IOException - after several timeouts and retransmissions
	 */
	static void sendRecv(DatagramSocket sock, TftpPacket blk, SocketAddress dst) throws IOException {
		for (int i = 0; i < DEFAULT_MAX_RETRIES; i++) {
//			System.err.println("sending Last Packet:" + blk.getByteCount() + "/size:" + blk.getBlockData().length);
			sock.send(new DatagramPacket(blk.getPacketData(), blk.getLength(), dst));
			try {
				byte[] buffer = new byte[MAX_TFTP_PACKET_SIZE];
				DatagramPacket msg = new DatagramPacket(buffer, buffer.length);

				sock.receive(msg); // waits for ACK
				TftpPacket ack = new TftpPacket(msg.getData(), msg.getLength());
				System.out.println("got ack:" + ack.getByteCount());
				if (ack.getOpcode() == OP_ACK)
					if (blk.getByteCount() <= ack.getByteCount()) {
						return;
					} else {
						System.err.println("wrong ack ignored, block= " + ack.getByteCount());
					}
				else
					System.err.println("error +++ (unexpected packet)");
			} catch (SocketTimeoutException e) {
			}
		}
		System.err.println("Too many retries!");
		throw new IOException("Too many retries");
	}

	/**
	 * Sends a window of datagrams and waits for an ACK
	 * 
	 * @param sock - Socket to use to communicate
	 * @param window - Window to send
	 * @param dst - Must include destination IP and port
	 * @throws SocketException
	 * @throws IOException
	 */
	static void goBackN(DatagramSocket sock, LinkedList<TftpPacket> window, SocketAddress dst) throws IOException {
		for (int i = 0; i < DEFAULT_MAX_RETRIES; i++) {
			Iterator<TftpPacket> it = window.iterator();

			while(it.hasNext()) {
				TftpPacket blk = it.next();
//				System.err.println("sending from GoBackN:" + blk.getByteCount() + "/size:" + blk.getBlockData().length);
				sock.send(new DatagramPacket(blk.getPacketData(), blk.getLength(), dst));
			}
			try {
				byte[] buffer = new byte[MAX_TFTP_PACKET_SIZE];
				DatagramPacket msg = new DatagramPacket(buffer, buffer.length);

				sock.receive(msg); // waits for ACK
				TftpPacket ack = new TftpPacket(msg.getData(), msg.getLength());
//				System.out.println("got ack:" + ack.getByteCount());
				if (ack.getOpcode() == OP_ACK) {
					updateWindow(ack.getByteCount());
					return;
				}
				else
					System.err.println("error +++ (unexpected packet)");
			} catch (SocketTimeoutException e) {
			}
		}
		System.err.println("Too many retries!");
		throw new IOException("Too many retries");
	}

	/**
	 * Updates the window's size, removing already ACKed packets
	 * 
	 * @param blockReceived - block number of the last ACK received from the client
	 */
	static void updateWindow(long blockReceived) {
		Iterator<TftpPacket> it = window.iterator();

		while(it.hasNext()) {
			TftpPacket pkt = it.next();
			long block = pkt.getByteCount();
			if(block < blockReceived) { // if blockReceived = block, we may have to send block next, don't remove block
				it.remove();
			}
		}
	}

	public static void main(String[] args) throws Exception {
		MyDatagramSocket.init(0, 2); // the second number is constant seed to have always the same random numbers
		switch (args.length) {
			case 4 :
				DEFAULT_MAX_RETRIES = Integer.valueOf(args[3]);
			case 3 :
				DEFAULT_TIMEOUT = Integer.valueOf(args[2]);
			case 2 :
				DEFAULT_BLOCKSIZE = Integer.valueOf(args[1]);
			case 1 :
				DEFAULT_PORT = Integer.valueOf(args[0]);
			case 0 :
				break;
			default :
				System.err.println("usage: java FTUDPServer [port [blocksize [timeout [retries]]]]");
				System.exit(0);
		}

		// create and bind socket to port for receiving client requests
		DatagramSocket mainSocket = new DatagramSocket(DEFAULT_PORT);
		System.out.println("New tftp server started at local port " + mainSocket.getLocalPort());

		for (;;) { // infinite processing loop...
			try {
				// prepare an empty datagram...
				byte[] buffer = new byte[MAX_TFTP_PACKET_SIZE];
				DatagramPacket msg = new DatagramPacket(buffer, buffer.length);

				mainSocket.receive(msg);

				// look at datagram as a TFTP packet
				TftpPacket req = new TftpPacket(msg.getData(), msg.getLength());
				switch (req.getOpcode()) {
					case OP_RRQ : // Read Request
						System.out.println("Read Request");

						// Launch a dedicated thread to handle the client
						// request...
						new Thread(new FTUDPServer(req, msg.getSocketAddress())).start();
						break;
					default : // unexpected packet op code!
						System.err.printf("? packet opcode %d ignored\n", req.getOpcode());
						sendError(mainSocket, 0, "Unknown request type..." + req.getOpcode(), msg.getSocketAddress());
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}
}
