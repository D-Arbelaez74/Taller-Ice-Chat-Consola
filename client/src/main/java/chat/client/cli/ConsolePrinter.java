package chat.client.cli;

/**
 * Salida sincronizada de la consola. Los callbacks de Ice llegan en otros hilos,
 * por eso toda impresion pasa por aqui y se vuelve a pintar el prompt.
 * Responsable: P2
 */
public class ConsolePrinter {

    public static final String PROMPT = "> ";

    // true mientras el prompt esta pintado esperando entrada
    private boolean promptVisible = false;

    public synchronized void info(String text) {
        print("[i] " + text);
    }

    public synchronized void event(String text) {
        print(text);
    }

    public synchronized void error(String text) {
        print("[!] " + text);
    }

    public synchronized void prompt() {
        if (!promptVisible) {
            System.out.print(PROMPT);
            System.out.flush();
            promptVisible = true;
        }
    }

    /** El usuario presiono Enter: el prompt ya no esta en pantalla. */
    public synchronized void inputConsumed() {
        promptVisible = false;
    }

    private void print(String line) {
        // Si el prompt estaba visible (evento asincrono), se sobrescribe y se vuelve a pintar
        System.out.print((promptVisible ? "\r" : "") + line + System.lineSeparator()
                + (promptVisible ? PROMPT : ""));
        System.out.flush();
    }
}
