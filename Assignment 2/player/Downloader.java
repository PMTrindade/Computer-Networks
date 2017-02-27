package player;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;

public class Downloader implements Runnable {
	
	String server;
	int port;
	LinkedBlockingQueue<Frame> buffer;
	int segmentSize;
	int[][] index;

	public Downloader(String server, int port, LinkedBlockingQueue<Frame> buffer, int segmentSize) {
		this.server = server;
		this.port = port;
		this.buffer = buffer;
		this.segmentSize = segmentSize;
	}

	public void run() {
			index = getIndex();

			int start = 0;
			int end = index[3][segmentSize];
			int next = segmentSize;

			while(next <= 265 && start != end) {
				try {
					Socket s1 = new Socket(server, port);
					// Obtem o canal de escrita associado ao socket.
					OutputStream os = s1.getOutputStream();
					// Obtem o canal de leitura associado ao socket.
					InputStream is = s1.getInputStream();

					DataInputStream dis = new DataInputStream(is);

					String req = "GET /Lifted-480p.dat HTTP/1.0\r\nRange: bytes="+start+"-"+end+"\r\n\r\n";

					os.write(req.getBytes()); // envia o request ao servidor

					String line = HTTPUtilities.readLine(is); // le a primeira linha da resposta

					String[] words = line.split(" "); // separa cada palavra por espacos

					String l;
					if(words[1].equals("200") || words[1].equals("206")) { // caso nao haja erros, codigo 200 ou 206 (ficheiro parcial)
						do {
							l = HTTPUtilities.readLine(is);
						} while (!l.equals(""));
						try {
							while(true) {
								byte[] buf = new byte[128 * 1024];
								int length = dis.readInt();
								long ts = dis.readLong();
								dis.readFully(buf, 0, length);
								Frame f = new Frame(length, ts, buf);
								buffer.add(f);
							}
						} catch (IOException e1) {
							start = index[3][next];
							next += segmentSize;
							if(next <= 265)
								end = index[3][next];
						}
					}
					s1.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			byte[] buf = new byte[128 * 1024];
			Frame f = new Frame(0, 0, buf);
			buffer.add(f);
	}

	public int[][] getIndex() {
		try {
			Socket s2 = new Socket(server, port);
			// Obtem o canal de escrita associado ao socket.
			OutputStream osI = s2.getOutputStream();
			// Obtem o canal de leitura associado ao socket.
			InputStream isI = s2.getInputStream();

			DataInputStream disI = new DataInputStream(isI);
			String reqIndex = "GET /Lifted-index.dat HTTP/1.0\r\n\r\n";

			osI.write(reqIndex.getBytes()); // envia o request ao servidor

			String line = HTTPUtilities.readLine(isI); // le a primeira linha da resposta

			String[] words = line.split(" "); // separa cada palavra por espacos

			index = new int[4][266]; // segundos
			int counter = 0;
			index[0][counter] = 0;
			index[1][counter] = 0;
			index[2][counter] = 0;
			index[3][counter] = 0;
			counter++;

			String l;
			if(words[1].equals("200") || words[1].equals("206")) { // caso nao haja erros, codigo 200 ou 206 (ficheiro parcial)
				do {
					l = HTTPUtilities.readLine(isI);
				} while (!l.equals(""));
				Scanner s = new Scanner(disI);
				s.nextLine();
				s.nextLine();
				s.nextLine();
				s.nextLine();
				s.nextLine();
				while(counter <= 265) {
					l = s.nextLine();
					words = l.split(" ");
					index[0][counter] = Integer.parseInt(words[2]); // 160p
					l = s.nextLine();
					words = l.split(" ");
					index[1][counter] = Integer.parseInt(words[2]); // 240p
					l = s.nextLine();
					words = l.split(" ");
					index[2][counter] = Integer.parseInt(words[2]); // 320p
					l = s.nextLine();
					words = l.split(" ");
					index[3][counter] = Integer.parseInt(words[2]); // 480p
					counter++;
				}
				s.close();
			}
			s2.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return index;
	}

}
