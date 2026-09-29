package chat.client;

import chat.client.callback.ClientCallbackI;
import chat.client.cli.CommandShell;
import chat.client.cli.ConsolePrinter;
import chat.client.files.FileController;
import chat.client.messaging.MessagingController;
import chat.client.session.SessionController;
import chat.client.voice.CallController;
import chat.contract.CallServicePrx;
import chat.contract.FileServicePrx;
import chat.contract.MessagingServicePrx;
import chat.contract.SessionServicePrx;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationData;
import com.zeroc.Ice.Properties;
import com.zeroc.Ice.Util;

import java.util.Scanner;

/**
 * Punto de entrada del cliente: conecta con el servidor, arma los controllers
 * de cada modulo y arranca la consola interactiva.
 */
public class ClientMain {

    public static void main(String[] args) {
        // config.client + overrides por linea de comandos (--Ice.* y --Chat.*)
        InitializationData init = new InitializationData();
        init.properties = Util.createProperties();
        init.properties.load("config.client");
        args = init.properties.parseIceCommandLineOptions(args);
        init.properties.parseCommandLineOptions("Chat", args);

        ConsolePrinter printer = new ConsolePrinter();

        try (Communicator communicator = Util.initialize(init)) {
            Properties props = communicator.getProperties();
            String endpoint = ":tcp -h " + props.getPropertyWithDefault("Chat.Server.Host", "127.0.0.1")
                    + " -p " + props.getPropertyAsIntWithDefault("Chat.Server.Port", 10000);

            SessionServicePrx session = SessionServicePrx.checkedCast(communicator.stringToProxy("Session" + endpoint));
            MessagingServicePrx messaging = MessagingServicePrx.checkedCast(communicator.stringToProxy("Messaging" + endpoint));
            FileServicePrx files = FileServicePrx.checkedCast(communicator.stringToProxy("Files" + endpoint));
            CallServicePrx calls = CallServicePrx.checkedCast(communicator.stringToProxy("Calls" + endpoint));
            if (session == null || messaging == null || files == null || calls == null) {
                printer.error("El servidor no expone todos los servicios esperados.");
                return;
            }
            printer.info("Conectado al servidor" + endpoint);

            ClientContext ctx = new ClientContext(communicator, session, messaging, files, calls, printer);

            SessionController sessionCtl = new SessionController(ctx);
            MessagingController messagingCtl = new MessagingController(ctx);
            FileController fileCtl = new FileController(ctx);
            CallController callCtl = new CallController(ctx);

            ClientCallbackI callback = new ClientCallbackI(sessionCtl, messagingCtl, fileCtl, callCtl);
            // TODO (P1): crear el ObjectAdapter del cliente, registrar 'callback', obtener su
            //            ClientCallbackPrx y hacer login antes de abrir la consola.

            CommandShell shell = new CommandShell(printer);
            sessionCtl.registerCommands(shell);
            messagingCtl.registerCommands(shell);
            fileCtl.registerCommands(shell);
            callCtl.registerCommands(shell);

            shell.run(new Scanner(System.in));
        } catch (com.zeroc.Ice.ConnectFailedException e) {
            printer.error("No se pudo conectar con el servidor. Esta corriendo? (" + e.getClass().getSimpleName() + ")");
        } catch (Exception e) {
            printer.error("Error fatal: " + e);
        }
    }
}
