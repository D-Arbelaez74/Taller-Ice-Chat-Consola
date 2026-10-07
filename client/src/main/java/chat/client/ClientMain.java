package chat.client;

import java.util.Scanner;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationData;
import com.zeroc.Ice.Properties;
import com.zeroc.Ice.Util;


import chat.client.callback.ClientCallbackI;
import chat.client.cli.CommandShell;
import chat.client.files.FileController;
import chat.client.messaging.MessagingController;
import chat.client.session.SessionController;
import chat.client.voice.CallController;
import chat.contract.CallServicePrx;
import chat.contract.FileServicePrx;
import chat.contract.MessagingServicePrx;
import chat.contract.SessionServicePrx;

// El cliente se conecta con el servidor y arranca la consola interactiva
public class ClientMain {

    public static void main(String[] args) {
        // config.client + overrides por linea de comandos (--Ice.* y --Chat.*)
        InitializationData init = new InitializationData();
        init.properties = Util.createProperties();
        init.properties.load("config.client");
        args = init.properties.parseIceCommandLineOptions(args);
        init.properties.parseCommandLineOptions("Chat", args);

        try (Communicator communicator = Util.initialize(init)) {
            Properties props = communicator.getProperties();
            String endpoint = ":tcp -h " + props.getPropertyWithDefault("Chat.Server.Host", "127.0.0.1")
                    + " -p " + props.getPropertyAsIntWithDefault("Chat.Server.Port", 10000);

            SessionServicePrx session = SessionServicePrx.checkedCast(communicator.stringToProxy("Session" + endpoint));
            MessagingServicePrx messaging = MessagingServicePrx.checkedCast(communicator.stringToProxy("Messaging" + endpoint));
            FileServicePrx files = FileServicePrx.checkedCast(communicator.stringToProxy("Files" + endpoint));
            CallServicePrx calls = CallServicePrx.checkedCast(communicator.stringToProxy("Calls" + endpoint));
            if (session == null || messaging == null || files == null || calls == null) {
                System.out.println("El servidor no expone todos los servicios esperados.");
                return;
            }
            System.out.println("Conectado al servidor " + endpoint);

            ClientContext ctx = new ClientContext(communicator, session, messaging, files, calls);

            SessionController sessionCtl = new SessionController(ctx);
            MessagingController messagingCtl = new MessagingController(ctx);
            FileController fileCtl = new FileController(ctx);
            CallController callCtl = new CallController(ctx);

            ClientCallbackI callback = new ClientCallbackI(sessionCtl, messagingCtl, fileCtl, callCtl);
            // Crear un adaptador para el cliente. Es como un mini-servidor que escucha peticiones
            // Usamos el puerto 0 para que el sistema operativo nos asigne uno aleatorio disponible
            // Así vamos a permitir que el cliente reciba notificaciones
            com.zeroc.Ice.ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("ClientAdapter", "tcp -h 127.0.0.1 -p 0");

            // Registramos la instancia de nuestro callback en este adaptador
            com.zeroc.Ice.ObjectPrx basePrx = adapter.addWithUUID(callback);
            
            // Activamos el adaptador para empezar a escuchar
            adapter.activate();

            // Convertimos el proxy general al proxy específico de nuestro contrato
            // O sea que el cliente ahora va a poder recibir notificaciones del servidor porque ahí se define
            chat.contract.ClientCallbackPrx callbackPrx = chat.contract.ClientCallbackPrx.checkedCast(basePrx);

            Scanner scanner = new Scanner(System.in);
            System.out.print("Ingresa tu nickname: ");
            String nickname = scanner.nextLine().trim(); // Siempre usando .trim() para eliminar los espacios y recibir el nickname limpio

            // Ejecutamos el login en el servidor y probamos que no falle por el nickname en uso o cualquier otro error, por eso los dos catch
            try {
                String[] onlineUsers = session.login(nickname, callbackPrx);
                ctx.setNickname(nickname); // Guardamos el nombre en el contexto compartido
                System.out.println("Usuarios en linea: " + String.join(", ", onlineUsers) + ". Registro exitoso.");
            } catch (chat.contract.NicknameInUseException e) {
                System.out.println("Ese nickname ya está en uso. Por favor reinicia e intenta con otro.");
                return;
            } catch (chat.contract.ChatException e) {
                System.out.println("Error al iniciar sesion: " + e.reason);
                return;
            }

            CommandShell shell = new CommandShell();
            sessionCtl.registerCommands(shell);
            messagingCtl.registerCommands(shell);
            fileCtl.registerCommands(shell);
            callCtl.registerCommands(shell);

            shell.run(scanner);
        } catch (com.zeroc.Ice.ConnectFailedException e) {
            System.out.println("No se pudo conectar con el servidor. Esta corriendo? (" + e.getClass().getSimpleName() + ")");
        } catch (Exception e) {
            System.out.println("Error fatal: " + e);
        }
    }
}
