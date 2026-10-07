package chat.client.cli;

import chat.contract.ChatException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Bucle de lectura de comandos (hilo principal), desacoplado de la impresion de eventos.
 * Cada modulo registra sus propios comandos con {@link #register}.
 * Responsable: P2
 */
public class CommandShell {

    @FunctionalInterface
    public interface Command {
        /** @param args texto despues del nombre del comando (puede ser vacio) */
        void execute(String args) throws Exception;
    }

    private record Entry(String usage, String description, Command action) {}

    private final Map<String, Entry> commands = new LinkedHashMap<>();
    private volatile boolean running = true;

    public CommandShell() {
        register("/help", "/help", "Muestra los comandos disponibles", args -> printHelp());
    }

    public void register(String name, String usage, String description, Command action) {
        commands.put(name.toLowerCase(), new Entry(usage, description, action));
    }

    public void stop() {
        running = false;
    }

    public void run(Scanner in) {
        System.out.println("Escriba /help para ver los comandos.");
        System.out.print("> ");
        while (running && in.hasNextLine()) {
            String line = in.nextLine().trim();
            if (!line.isEmpty()) {
                dispatch(line);
            }
            if (running) {
                System.out.print("> ");
            }
        }
    }

    private void dispatch(String line) {
        String[] parts = line.split("\\s+", 2);
        Entry entry = commands.get(parts[0].toLowerCase());
        if (entry == null) {
            System.out.println("Comando desconocido: " + parts[0] + " (use /help)");
            return;
        }
        try {
            entry.action().execute(parts.length > 1 ? parts[1] : "");
        } catch (ChatException e) {
            System.out.println(e.reason);
        } catch (Exception e) {
            System.out.println("Fallo al ejecutar " + parts[0] + ": " + e);
        }
    }

    private void printHelp() {
        StringBuilder sb = new StringBuilder("Comandos:");
        commands.values().forEach(e ->
                sb.append(System.lineSeparator()).append(String.format("  %-32s %s", e.usage(), e.description())));
        System.out.println(sb.toString());
    }
}
