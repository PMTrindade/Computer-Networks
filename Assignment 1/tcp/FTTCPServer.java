package tcp;

/**
 * FTTCPServer - servidor em TCP para transferencia de ficheiros
 * para um cliente TCP.
 * Ver enunciado do trabalho 1 - RC 2014/2015
 */

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class FTTCPServer {

	static final int BLOCKSIZE = 512;
	public static final int PORT = 8000;

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		// Cria o socket de atendimento deste servidor, de onde
		// aceita as conex›es dos clientes
		ServerSocket serverSocket = new ServerSocket( PORT );

		for(;;) { // ciclo infinito de atendimento
			System.out.println("Server ready at port "+PORT);
			// Espera ate que um cliente se ligue,
			// retorna um novo socket para comunicar com esse cliente
			Socket clientSocket = serverSocket.accept();

			// ObtŽm o canal de leitura do socket para
			// receber dados do cliente
			InputStream is = clientSocket.getInputStream();

			byte[] buf = new byte[BLOCKSIZE];
			int n;
			int blocksCount = 0;

			for ( n=0; n<BLOCKSIZE; n++ ) {  // le nome do ficheiro que o cliente envia
				int s = is.read();
				if ( s!=-1 ) buf[n]=(byte)s;
				else System.exit(1);
				if ( buf[n] == 0 ) break;
			}
			System.out.println("Receiving: '"+new String(buf, 0, n)+"'");
			FileOutputStream f = new FileOutputStream("tmp.out");

			long time = System.currentTimeMillis();
			while( (n = is.read( buf )) > 0 ) { // escreve ficheiro recebido
				f.write( buf, 0, n );
				blocksCount++;
			}

			time = System.currentTimeMillis() - time;
			// Fecha e liberta o socket que usou para comunicar com este cliente...
			clientSocket.close();
			// fecha ficheiro recebido
			f.close();

			System.out.println("A recepção do ficheiro demorou: "+ time +" ms");
			System.out.println("Blocos enviados e reenviados: "+ blocksCount);
		}

	}

}
