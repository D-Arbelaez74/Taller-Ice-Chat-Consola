
#pragma once

[["java:package:chat"]]
module contract
{
    sequence<string> StringSeq; //Aquí se transmiten listas de usuarios
    sequence<byte> ByteSeq; //Aquí ya son los bloques de archivos

    // La excepción grande del negocio. Como la excepcion padre de la que se herdan las otras.
    exception ChatException
    {
        string reason;
    }

    //Excepciones para casos especificos
    exception NicknameInUseException extends ChatException {} //Por un nombre repetido
    exception UserNotFoundException extends ChatException {} //Por usuario no encontrado
    exception NotLoggedInException extends ChatException {} //Por no haber hecho login
}
