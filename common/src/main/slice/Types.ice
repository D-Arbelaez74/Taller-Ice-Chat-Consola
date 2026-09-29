// Tipos y excepciones compartidos por todos los contratos.
// Responsable: P1 (cambios acordados con todo el equipo)
#pragma once

[["java:package:chat"]]
module contract
{
    sequence<string> StringSeq;
    sequence<byte> ByteSeq;

    // Excepcion base de negocio
    exception ChatException
    {
        string reason;
    }

    exception NicknameInUseException extends ChatException {}
    exception UserNotFoundException extends ChatException {}
    exception NotLoggedInException extends ChatException {}
}
