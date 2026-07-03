package studio.modroll.critfall.neoforge.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;

/**
 * Runs {@code /critfall} through the REAL command dispatcher — not by calling the handler
 * methods directly. M3's unit-style coverage exercised the command logic but not its registration
 * on the server, which nearly shipped broken; these tests fail if the tree is not registered,
 * parses differently, or the raycast default stops finding entities.
 */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommandGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void inspectWithSelectorExecutesThroughTheDispatcher(GameTestHelper helper) {
        Pig pig = spawnPig(helper, 1, 1);
        int result = execute(helper, serverSource(helper), "critfall inspect " + pig.getStringUUID());
        if (result != 1) {
            helper.fail("/critfall inspect <entity> must succeed through the dispatcher, returned " + result);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void inspectWithoutArgumentRaycastsTheCrosshairEntity(GameTestHelper helper) {
        // One block above the other tests' spawn height: entities at y=1 sit inside the test
        // platform's floor layer, which would occlude the eye-level ray with the floor surface.
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 3));
        pig.setNoAi(true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 position = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getEyePosition());
        CommandSourceStack source = serverSource(helper).withEntity(player);
        int result = execute(helper, source, "critfall inspect");
        if (result != 1) {
            helper.fail("/critfall inspect with no argument must inspect the looked-at pig, returned " + result);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void checkWithItemArgumentExecutesThroughTheDispatcher(GameTestHelper helper) {
        int result = execute(helper, serverSource(helper), "critfall check minecraft:iron_sword");
        if (result != 1) {
            helper.fail("/critfall check <item> must succeed through the dispatcher, returned " + result);
        }
        helper.succeed();
    }

    private static Pig spawnPig(GameTestHelper helper, int x, int z) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(x, 1, z));
        pig.setNoAi(true);
        return pig;
    }

    private static CommandSourceStack serverSource(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
    }

    private static int execute(GameTestHelper helper, CommandSourceStack source, String command) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            helper.fail("command \"" + command + "\" failed to parse/execute: " + e.getMessage());
            return 0;
        }
    }
}
