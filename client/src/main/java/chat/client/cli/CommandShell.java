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
    private final ConsolePrinter printer;
    private volatile boolean running = true;

    public CommandShell(ConsolePrinter printer) {
        this.printer = printer;
        register("/help", "/help", "Muestra los comandos disponibles", args -> printHelp());
    }

    public void register(String name, String usage, String description, Command action) {
        commands.put(name.toLowerCase(), new Entry(usage, description, action));
    }

    public void stop() {
        running = false;
    }

    public void run(Scanner in) {
        printer.info("Escriba /help para ver los comandos.");
        printer.prompt();
        while (running && in.hasNextLine()) {
            String line = in.nextLine().trim();
            printer.inputConsumed();
            if (!line.isEmpty()) {
                dispatch(line);
            }
            if (running) {
                printer.prompt();
            }
        }
    }

    private void dispatch(String line) {
        String[] parts = line.split("\\s+", 2);
        Entry entry = commands.get(parts[0].toLowerCase());
        if (entry == null) {
            printer.error("Comando desconocido: " + parts[0] + " (use /help)");
            return;
        }
        try {
            entry.action().execute(parts.length > 1 ? parts[1] : "");
        } catch (ChatException e) {
            printer.error(e.reason);
        } catch (Exception e) {
            printer.error("Fallo al ejecutar " + parts[0] + ": " + e);
        }
    }

    private void printHelp() {
        StringBuilder sb = new StringBuilder("Comandos:");
        commands.values().forEach(e ->
                sb.append(System.lineSeparator()).append(String.format("  %-32s %s", e.usage(), e.description())));
        printer.event(sb.toString());
    }
}
