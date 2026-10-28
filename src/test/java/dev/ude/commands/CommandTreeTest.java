package dev.ude.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import java.util.Set;
import java.util.stream.Collectors;

/** Verifies command paths independently of a running world. */
public final class CommandTreeTest {
    public static void main(String[] args) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        UdeCommands.register(dispatcher);
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("ude");
        Set<String> expected = Set.of("help", "bank", "market", "credit", "home", "business", "money", "cityhall");
        if (!root.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet()).equals(expected))
            throw new AssertionError("Unexpected root commands: " + root.getChildren());
        for (String path : new String[] {"credit take type amount days", "credit list", "credit repay loan amount",
                "bank transfer player amount", "home set", "business buy type", "money grant player amount", "cityhall build"}) {
            CommandNode<CommandSourceStack> node = root;
            for (String part : path.split(" ")) {
                node = node.getChild(part);
                if (node == null) throw new AssertionError("Missing command path: " + path);
            }
            if (node.getCommand() == null) throw new AssertionError("No handler: " + path);
        }
        if (root.getChild("credit").getChildren().size() != 3)
            throw new AssertionError("Unrelated commands nested under credit");
        if (!root.getChild("cityhall").getChild("build").getChildren().isEmpty())
            throw new AssertionError("Root command tree nested under cityhall");
        System.out.println("PASS: command hierarchy and 8 executable paths");
    }
}
