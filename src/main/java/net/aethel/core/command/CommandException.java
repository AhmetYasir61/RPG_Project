package net.aethel.core.command;

/**
 * Komut govdesinden firlatilirsa cerceve bunu hata olarak degil, kullaniciya
 * gosterilecek dil anahtari olarak isler; stack trace loglanmaz.
 */
public class CommandException extends RuntimeException {

    private final String messageKey;

    public CommandException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
