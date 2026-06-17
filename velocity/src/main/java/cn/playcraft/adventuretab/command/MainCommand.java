package cn.playcraft.adventuretab.command;

import cn.playcraft.adventuretab.AdventureTab;
import cn.playcraft.adventuretab.config.Messages;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainCommand implements SimpleCommand {

    private final AdventureTab plugin;

    public MainCommand(AdventureTab plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(Messages.get("cmd-help-header"));
            source.sendMessage(Messages.get("cmd-help-reload"));
            source.sendMessage(Messages.get("cmd-help-info"));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!source.hasPermission("adventuretab.admin")) {
                    source.sendMessage(Messages.get("cmd-no-permission"));
                    return;
                }
                plugin.reload();
                source.sendMessage(Messages.get("cmd-reloaded"));
            }
            case "info" -> {
                int online = plugin.getServer().getPlayerCount();
                int servers = plugin.getServer().getAllServers().size();
                boolean lp = plugin.getLuckPermsHook().isAvailable();
                boolean ce = plugin.getCraftEngineHook().isAvailable();
                String connected = Messages.raw("info-connected");
                String disconnected = Messages.raw("info-disconnected");
                source.sendMessage(Messages.get("info-header"));
                source.sendMessage(Messages.get("info-online", online));
                source.sendMessage(Messages.get("info-servers", servers));
                source.sendMessage(Messages.get("info-luckperms", lp ? connected : disconnected));
                source.sendMessage(Messages.get("info-craftengine", ce ? connected : disconnected));
            }
            default -> source.sendMessage(
                    Messages.get("cmd-unknown", args[0]));
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return CompletableFuture.completedFuture(
                    List.of("reload", "info").stream().filter(s -> s.startsWith(prefix)).toList());
        }
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("adventuretab.use");
    }
}
