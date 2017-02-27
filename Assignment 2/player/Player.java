package player;

import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;

public class Player {
	
	public static void play(LinkedBlockingQueue<Frame> buffer) {
		try {
			Viewer viewer = new Viewer(1280, 720);

			long time;
			long t0 = System.nanoTime();
			Frame f;
			while((f = buffer.take()).getLength() > 0) {
				long ts = f.getTimeStamp();
				long t1 = System.nanoTime();
				time = t1 - t0;
				Thread.sleep(Math.max(0, ts - time)/1000000); // sleep esta em milisegundos, ts e time estao em nanosegundos
				viewer.updateFrame(f.getImage(), f.getLength());
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			System.out.println("usage: java Player URL playoutDelay segmentSize");
			System.out.println("usage: java Player http://localhost:8080/ficheiro...");
			System.out.println("usage: java Player http://localhost:8080/Lifted-160p.dat");
			System.out.println("usage: java Player http://localhost:8080/Lifted-240p.dat");
			System.out.println("usage: java Player http://localhost:8080/Lifted-320p.dat");
			System.out.println("usage: java Player http://localhost:8080/Lifted-480p.dat 5 5");
			System.exit(0);
		}

		String arg = args[0];
		URL url = new URL(arg);
		String server = url.getHost();
		int port = url.getPort();

		int playoutDelay = Integer.parseInt(args[1]);
		int segmentSize = Integer.parseInt(args[2]);

		LinkedBlockingQueue<Frame> buffer = new LinkedBlockingQueue<Frame>();

		Downloader d = new Downloader(server, port, buffer, segmentSize);
		new Thread(d).start();

		Thread.sleep(playoutDelay*1000);

		play(buffer);
	}

}
