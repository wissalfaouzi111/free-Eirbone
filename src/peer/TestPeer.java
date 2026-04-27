import java.io.*;
import java.net.*;
import java.util.*;

public class TestPeer {

    public static void main(String[] args) throws Exception {
        // Crear archivo de prueba
        createTestFile("test.dat", 1024 * 10); // 10 piezas de 1024 bytes

        // Iniciar seeder (peer con archivo completo)
        Thread seederThread = new Thread(() -> {
            try {
                startSeeder();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        seederThread.start();

        // Esperar un poco para que el seeder inicie
        Thread.sleep(1000);

        // Iniciar leecher (peer que descarga)
        Thread leecherThread = new Thread(() -> {
            try {
                startLeecher();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        leecherThread.start();

        // Esperar a que terminen
        seederThread.join();
        leecherThread.join();
    }

    private static void createTestFile(String filename, int size) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            byte[] data = new byte[size];
            new Random().nextBytes(data);
            fos.write(data);
        }
        System.out.println("Created test file: " + filename);
    }

    private static void startSeeder() throws Exception {
        // Simular seeder: peer con archivo completo
        Config config = new Config();
        config.listenPort = "5001";
        config.files = new File[]{new File("test.dat")};

        PeerState state = new PeerState();
        // Simular que tiene el archivo completo
        FileInfo fileInfo = new FileInfo("test.dat", 1024 * 10, 1024, "testkey");
        DownloadState ds = new DownloadState(fileInfo);
        // Marcar todas las piezas como disponibles
        for (int i = 0; i < fileInfo.totalPieces(); i++) {
            ds.markPiece(i);
        }
        state.addDownload(ds);

        PeerServer server = new PeerServer(config, state);
        server.run(); // Esto bloqueará, pero en un hilo separado
    }

    private static void startLeecher() throws Exception {
        // Simular leecher: conectarse al seeder y solicitar piezas
        Socket socket = new Socket("localhost", 5001);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

        // Enviar interested
        writer.println("interested testkey");

        // Leer respuesta have
        String response = reader.readLine();
        System.out.println("Leecher received: " + response);

        // Enviar getpieces
        writer.println("getpieces testkey [0 1 2]");

        // Leer header data
        String dataHeader = reader.readLine();
        System.out.println("Leecher received data header: " + dataHeader);

        // Leer piezas (binario)
        // Aquí necesitarías parsear el header y leer los bytes correspondientes
        // Para simplificar, asumir que lee 3 piezas de 1024 bytes cada una
        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[1024];
        for (int i = 0; i < 3; i++) {
            int read = in.read(buffer);
            System.out.println("Read piece " + i + ": " + read + " bytes");
        }

        socket.close();
    }
}