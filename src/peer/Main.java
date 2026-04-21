package peer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Base64;

public class Main {
    private static final String DOWNLOADS_DIR = "data/downloads";
    private static final int SERVER_PORT = 2222;
    private static PeerState peerState;

    public static void main(String[] args) throws Exception {
        // Crear directorios si no existen
        new File(DOWNLOADS_DIR).mkdirs();
        new File("data/incomplete").mkdirs();
        new File("data/complete").mkdirs();

        // Cargar estado de archivos locales
        peerState = createPeerStateFromDownloads();

        // Iniciar servidor
        Config config = new Config();
        config.listenPort = String.valueOf(SERVER_PORT);
        PeerServer server = new PeerServer(config, peerState);
        Thread serverThread = new Thread(() -> {
            try {
                System.out.println("[SERVER] Iniciando servidor en puerto " + SERVER_PORT);
                server.run();
            } catch (Exception e) {
                System.out.println("[SERVER] Error: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Esperar un poco para que inicie
        Thread.sleep(1000);

        if (args.length >= 1 && args[0].equals("debug_download") && args.length == 4) {
            // Modo debug descarga completa: debug_download <ip> <port> <key>
            String ip = args[1];
            int port = Integer.parseInt(args[2]);
            String key = args[3];
            runDebugDownload(ip, port, key);
        } else {
            // Modo interactivo
            runInteractiveMode();
        }
    }

    private static void runInteractiveMode() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n=== MENU INTERACTIVO ===");
            System.out.println("1. listen - Mostrar archivos servidos y escuchar conexiones");
            System.out.println("2. download <ip> <port> <key> - Descargar archivo completo");
            System.out.println("3. list - Listar archivos disponibles localmente");
            System.out.println("4. exit - Salir");
            System.out.print("Selecciona opción: ");
            String input = scanner.nextLine().trim();

            if (input.equals("1") || input.startsWith("listen")) {
                showServedFiles();
                System.out.println("[INFO] Servidor escuchando en puerto " + SERVER_PORT + ". Presiona Ctrl+C para salir.");
                // Quedarse escuchando (el servidor ya está corriendo)
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    break;
                }
            } else if (input.startsWith("2") || input.startsWith("download ")) {
                String[] parts = input.split("\\s+");
                if (parts.length == 4) {
                    try {
                        String ip = parts[1];
                        int port = Integer.parseInt(parts[2]);
                        String key = parts[3];
                        runDebugDownload(ip, port, key);
                    } catch (Exception e) {
                        System.out.println("[ERROR] Formato inválido. Usa: download <ip> <port> <key>");
                    }
                } else {
                    System.out.println("[ERROR] Usa: download <ip> <port> <key>");
                }
            } else if (input.equals("3") || input.startsWith("list")) {
                listAvailableFiles();
            } else if (input.equals("4") || input.equals("exit")) {
                break;
            } else {
                System.out.println("[ERROR] Opción inválida.");
            }
        }
        scanner.close();
    }

    private static void showServedFiles() {
        System.out.println("[INFO] Archivos siendo servidos:");
        for (DownloadState ds : peerState.downloads.values()) {
            FileInfo fi = ds.file;
            System.out.println("- " + fi.getFilename() + " | key=" + fi.getKey() + " | piezas=" + fi.totalPieces() + " | completo=" + ds.isComplete());
        }
        if (peerState.downloads.isEmpty()) {
            System.out.println("  (Ninguno)");
        }
    }

    private static void listAvailableFiles() {
        File dir = new File(DOWNLOADS_DIR);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("[DEBUG] No hay archivos en " + DOWNLOADS_DIR);
            return;
        }

        System.out.println("[DEBUG] Archivos disponibles:");
        for (File file : files) {
            if (file.isFile()) {
                try {
                    FileChunker chunker = new FileChunker(file);
                    FileMetadata meta = chunker.getMetadata();
                    System.out.println("- " + meta.getFilename() + " | size=" + meta.getLength() +
                                       " | pieces=" + meta.getNumberOfPieces() + " | key=" + meta.getKey());
                } catch (IOException e) {
                    System.out.println("- " + file.getName() + " | Error: " + e.getMessage());
                }
            }
        }
    }

    private static void runDebugDownload(String ip, int port, String key) throws IOException {
        System.out.println("[CLIENT] Conectando a " + ip + ":" + port + " para key=" + key);

        try (Socket socket = new Socket(ip, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            // Enviar interested
            writer.println("interested " + key);
            System.out.println("[CLIENT] Enviado: interested " + key);

            // Recibir have
            String response = reader.readLine();
            System.out.println("[CLIENT] Recibido: " + response);
            if (!response.startsWith("have " + key + " ")) {
                System.out.println("[CLIENT] Respuesta inesperada");
                return;
            }

            String base64 = response.substring(("have " + key + " ").length());
            BitSet remoteBufferMap = decodeBufferMap(base64, 10000); // Asumir max piezas
            System.out.println("[CLIENT] BufferMap remoto: " + remoteBufferMap.cardinality() + " piezas disponibles");

            // Solicitar todas las piezas disponibles
            List<Integer> toRequest = new ArrayList<>();
            for (int i = 0; i < remoteBufferMap.length(); i++) {
                if (remoteBufferMap.get(i)) {
                    toRequest.add(i);
                }
            }

            if (toRequest.isEmpty()) {
                System.out.println("[CLIENT] No hay piezas disponibles");
                return;
            }

            // Enviar getpieces
            StringBuilder sb = new StringBuilder("getpieces " + key + " [");
            for (int i = 0; i < toRequest.size(); i++) {
                if (i > 0) sb.append(" ");
                sb.append(toRequest.get(i));
            }
            sb.append("]");
            writer.println(sb.toString());
            System.out.println("[CLIENT] Enviado: getpieces con " + toRequest.size() + " piezas");

            // Recibir data header
            String dataHeader = reader.readLine();
            System.out.println("[CLIENT] Recibido header: " + dataHeader);

            // Parsear header para obtener lista de (idx, size)
            List<int[]> piecesInfo = parseDataHeader(dataHeader, key);
            if (piecesInfo.isEmpty()) {
                System.out.println("[CLIENT] Header inválido");
                return;
            }

            // Crear DownloadState para el archivo
            int totalSize = piecesInfo.stream().mapToInt(p -> p[1]).sum();
            FileInfo fileInfo = new FileInfo("downloaded_" + key, totalSize, 1024, key);
            DownloadState ds = new DownloadState(fileInfo);

            // Leer y escribir cada pieza
            InputStream in = socket.getInputStream();
            for (int[] piece : piecesInfo) {
                int idx = piece[0];
                int size = piece[1];
                byte[] data = new byte[size];
                int read = in.read(data);
                if (read == size) {
                    ds.writePiece(idx, data);
                    System.out.println("[CLIENT] Escrita pieza " + idx + " (" + size + " bytes)");
                } else {
                    System.out.println("[CLIENT] Error leyendo pieza " + idx + ", bytes leídos: " + read);
                }
            }

            // Verificar si está completo
            if (ds.isComplete()) {
                ds.getStorage().finalizeDownload("downloaded_" + key + ".complete");
                System.out.println("[CLIENT] Descarga completada y archivo finalizado en data/complete/");
            } else {
                System.out.println("[CLIENT] Descarga parcial completada (" + ds.getBufferMap().cardinality() + "/" + fileInfo.totalPieces() + " piezas)");
            }
        }
    }

    private static List<int[]> parseDataHeader(String header, String expectedKey) {
        List<int[]> pieces = new ArrayList<>();
        // Formato: data <key> [<idx> <size> ...]
        String[] parts = header.split("\\s+");
        if (parts.length < 3 || !parts[0].equals("data") || !parts[1].equals(expectedKey)) {
            return pieces;
        }

        // Encontrar [ y ]
        int start = header.indexOf('[');
        int end = header.lastIndexOf(']');
        if (start == -1 || end == -1 || end <= start) {
            return pieces;
        }

        String inside = header.substring(start + 1, end).trim();
        String[] tokens = inside.split("\\s+");
        for (int i = 0; i < tokens.length; i += 2) {
            if (i + 1 < tokens.length) {
                try {
                    int idx = Integer.parseInt(tokens[i]);
                    int size = Integer.parseInt(tokens[i + 1]);
                    pieces.add(new int[]{idx, size});
                } catch (NumberFormatException e) {
                    // Ignorar
                }
            }
        }
        return pieces;
    }

    private static PeerState createPeerStateFromDownloads() throws IOException {
        PeerState state = new PeerState();
        File dir = new File(DOWNLOADS_DIR);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    FileChunker chunker = new FileChunker(file);
                    FileMetadata meta = chunker.getMetadata();
                    FileInfo fileInfo = new FileInfo(meta.getFilename(), meta.getLength(), meta.getPieceSize(), meta.getKey());
                    DownloadState ds = new DownloadState(fileInfo);
                    // Marcar todas piezas como disponibles
                    for (int i = 0; i < fileInfo.totalPieces(); i++) {
                        ds.markPiece(i);
                    }
                    state.addDownload(ds);
                }
            }
        }
        return state;
    }

    private static BitSet decodeBufferMap(String base64, int totalPieces) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        BitSet bs = new BitSet(totalPieces);
        for (int i = 0; i < totalPieces; i++) {
            if (i / 8 < bytes.length) {
                if ((bytes[i / 8] & (1 << (7 - (i % 8)))) != 0) {
                    bs.set(i);
                }
            }
        }
        return bs;
    }
}