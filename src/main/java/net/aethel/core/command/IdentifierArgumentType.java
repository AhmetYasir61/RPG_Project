package net.aethel.core.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.List;

/**
 * Bosluga kadar her seyi okuyan metin argumani.
 *
 * Brigadier'in kendi {@code StringArgumentType.word()} tipi iki nokta kabul
 * ETMEZ: izin verilen karakterler harf, rakam, _ - . ve + ile sinirlidir. Bu
 * yuzden "aethel:alev_dalgasi" gibi namespace'li bir kimlik yazilamiyor,
 * istemci "Expected whitespace to end one argument" hatasi veriyordu -- yani
 * projedeki HER kimlik argumani kullanilamaz durumdaydi.
 *
 * Tirnakli bicim de desteklenmez cunku kimliklerde bosluk yoktur; boslukta
 * durmak, sonraki argumanin dogru ayrismasini garanti eder.
 */
public final class IdentifierArgumentType implements ArgumentType<String> {

    private static final IdentifierArgumentType INSTANCE = new IdentifierArgumentType();

    private IdentifierArgumentType() {}

    public static IdentifierArgumentType identifier() {
        return INSTANCE;
    }

    public static String get(CommandContext<?> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public java.util.Collection<String> getExamples() {
        return List.of("alev_kilici", "aethel:alev_kilici");
    }
}
